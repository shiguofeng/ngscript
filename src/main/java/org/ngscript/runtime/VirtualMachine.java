/*
 *  wssccc all rights reserved
 */
package org.ngscript.runtime;

import org.ngscript.compiler.Instruction;
import org.ngscript.j2se.DrawWindow;
import org.ngscript.runtime.opcache.OpBinding;
import org.ngscript.runtime.opcache.OpMap;
import org.ngscript.runtime.vo.FunctionDefinition;
import org.ngscript.runtime.vo.VmMemRef;
import org.ngscript.runtime.vo.VmMethod;
import org.ngscript.utils.FastStack;

import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author wssccc <wssccc@qq.com>
 */
public class VirtualMachine {

    HashMap<String, Method> cpuMethodCache = new HashMap<String, Method>();
    //static data
    OpBinding[] instructions;
    HashMap<String, Integer> labels = new HashMap<String, Integer>();

    HashMap<String, String> imported = new HashMap<String, String>();
    FastStack<Context> machine_state_stack = new FastStack<Context>(32);
    FastStack<Context> contextStack = new FastStack<Context>(32);

    //machine states
    Instruction helptext;
    FastStack<Object> stack = new FastStack<>(32);
    FastStack<FunctionDefinition> callstack = new FastStack<FunctionDefinition>();
    //temp var for clear callStackSize op
    int call_stack_size;
    //registers
    public final VmMemRef eax = new VmMemRef();
    public final VmMemRef exception = new VmMemRef();

    public final VmMemRef env = new VmMemRef();
    int eip = 0;
    //

    PrintWriter out;
    PrintWriter err;

    public VirtualMachine(PrintWriter out, PrintWriter err) {
        this.out = out;
        this.err = err;
        //init register
        eip = 0;
        env.write(new Environment(null));
        init_builtins(((Environment) env.read()).data);
        //init_builtins(func);
    }

    public void printEax(boolean highlight) {
        if (eax.read() != null) {
            out.println((highlight ? "[[b;white;black]%eax] = " : "%eax = ") + ((eax.read() == null ? "null" : eax.read()) + " (" + (eax.read() == null ? "null" : (eax.read().getClass().isAnonymousClass() ? eax.read().getClass().getSuperclass().getSimpleName() : eax.read().getClass().getSimpleName()))) + ")");
        }
    }

    public VmMemRef lookup(String member) throws VmRuntimeException {
        return ((Environment) env.read()).lookup(member, this, false);
    }

    public Class[] getParamTypes(int offset) {
        Object[] params = (Object[]) stack.peek(offset);
        Class types[] = new Class[params.length];
        for (int i = 0; i < types.length; i++) {
            if (params[i] == null) {
                types[i] = null;
            } else {
                types[i] = params[i].getClass();
            }
        }

        return types;
    }

    public void loadInstructions(ArrayList<Instruction> ins2) {
        Map<String, InvokableInstruction> map = OpMap.INSTANCE.getMap();
        //
        ArrayList<OpBinding> ins = new ArrayList<>();
        for (Instruction instruction : ins2) {
            ins.add(new OpBinding(instruction, map.get(instruction.op)));
        }
        //
        loadLabels(ins);
        //reset eip
        eip = 0;
        instructions = ins.toArray(new OpBinding[0]);
    }

    final void loadLabels(ArrayList<OpBinding> ins) {
        for (int i = 0; i < ins.size(); ++i) {
            if (ins.get(i).op.equals("label")) {
                labels.put(ins.get(i).param, i);
            }
        }
    }

    final void init_builtins(HashMap<String, VmMemRef> map) {
        map.put("println", new VmMemRef(new VmMethod() {
            @Override
            public void invoke(VirtualMachine vm, Object[] vars) {
                for (Object var : vars) {
                    out.print(var);
                }
                out.println();
                out.flush();
            }
        }));
        map.put("showWindow", new VmMemRef(new VmMethod() {
            @Override
            public void invoke(VirtualMachine vm, Object[] vars) {
                java.awt.EventQueue.invokeLater(new Runnable() {

                    @Override
                    public void run() {
                        new DrawWindow().setVisible(true);
                    }
                });
            }
        }));
        map.put("draw", new VmMemRef(new VmMethod() {
            @Override
            public void invoke(VirtualMachine vm, Object[] vars) {
                int x = (Integer) vars[0];
                int y = (Integer) vars[1];
                int r = (Integer) vars[2];
                int g = (Integer) vars[3];
                int b = (Integer) vars[4];
                DrawWindow.draw(x, y, r, g, b);
            }
        }));
        map.put("print", new VmMemRef(new VmMethod() {
            @Override
            public void invoke(VirtualMachine vm, Object[] vars) {
                for (Object var : vars) {
                    out.print(var);
                }
                out.flush();
            }
        }));
        map.put("Object", new VmMemRef(new VmMethod() {
            @Override
            public void invoke(VirtualMachine vm, Object[] vars) {
                //prepare env
                Environment env = new Environment((Environment) vm.env.read());
                vm.env.write(env);
            }
        }));
        map.put("Coroutine", new VmMemRef(Coroutine.class));
        //init coroutine return-hook
//        instructions.add(new Instruction("jmp", "coroutine_return_hook_exit"));
//        instructions.add(new Instruction("coroutine_return"));
//        labels.put("coroutine_return_hook", 1);
//        labels.put("coroutine_return_hook_exit", instructions.size());
    }

    public void run() throws InvocationTargetException, VmRuntimeException, Exception {
        //initial
        exception.write(null);
        eax.write(null);
        while (true) {
            if (eip < 0 || eip >= instructions.length) {
                //halted, try upper context
                if (!contextStack.isEmpty()) {
                    Context lastContext = contextStack.pop();
                    lastContext.restore(this);
                } else {
                    return;
                }
            }

            OpBinding instruction = instructions[eip];
            ++eip;
            if (instruction.op.equals("//")) {
                helptext = instruction;
                continue;
            }
            //System.out.println("run " + eip + "\t" + instruction);
            try {
                //instant accleration
                //AutoCreatedCpuDispatcher.dispatch(instruction, this);
                instruction.invoke(this);
//                if (AutoCreatedCpuDispatcher.dispatch(instruction, this)) {
//                    continue;
//                }
//                Method m;
//                if (cpuMethodCache.containsKey(instruction.op)) {
//                    m = cpuMethodCache.get(instruction.op);
//                } else {
//                    m = InterpreterUtils.class.getMethod(instruction.op, VirtualMachine.class, String.class, String.class);
//                    cpuMethodCache.put(instruction.op, m);
//                }
//                m.invoke(InterpreterUtils.class, this, instruction.param, instruction.paramExtended);

            } catch (Exception ex) {
                try {
                    //System.out.println(ex.getCause().toString());
                    //for detail
                    System.out.println("eip=" + eip);
                    Logger.getLogger(VirtualMachine.class.getName()).log(Level.SEVERE, null, ex);
                    exception.write(ex);
                    InterpreterUtils.restore_machine_state(this, null, null);
                } catch (VmRuntimeException ex1) {
                    err.println("VM Exception");
                    err.println(ex1.toString());
                    Logger.getLogger(VirtualMachine.class.getName()).log(Level.SEVERE, null, ex1.getCause());
                    throw ex1; //do not hold this type
                }
            }
        }
    }
}
