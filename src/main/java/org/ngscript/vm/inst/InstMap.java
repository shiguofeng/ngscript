package org.ngscript.vm.inst;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtNewConstructor;
import javassist.CtNewMethod;
import org.apache.commons.lang3.StringUtils;
import org.ngscript.vm.InvokableInstruction;
import org.ngscript.vm.VmCpu;
import org.ngscript.vm.WscVM;

import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class InstMap {

    public Map<String, InvokableInstruction> map = new HashMap<>();
    ClassPool classPool = new ClassPool(true);

    public InstMap() {
        init();
    }

    public void init() {
        classPool.importPackage("org.ngscript.vm");
        Class clazz = VmCpu.class;
        Method[] methods = clazz.getMethods();
        for (Method m : methods) {
            Class[] cls = m.getParameterTypes();
            if (cls.length > 0 && cls[0] == WscVM.class) {
                try {
                    map.put(m.getName(), toInstruction(m.getName()));
                    map.put(m.getName() + "_pe", toInstructionPe(m.getName()));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private InvokableInstruction toInstruction(String inst) throws Exception {
        CtClass mCtc = classPool.makeClass(InvokableInstruction.class.getName() + StringUtils.capitalize(inst));
        mCtc.addInterface(classPool.get(InvokableInstruction.class.getName()));
        mCtc.addConstructor(CtNewConstructor.defaultConstructor(mCtc));
        mCtc.addMethod(CtNewMethod.make("public void invoke(WscVM vm, String param, String param_extend) throws WscVMException { VmCpu." + inst + "(vm,param,param_extend);}", mCtc));
        Class pc = mCtc.toClass();
        InvokableInstruction bytecodeProxy = (InvokableInstruction) pc.newInstance();

        FileOutputStream fos = new FileOutputStream("target/" + pc.getSimpleName() + ".class");
        fos.write(mCtc.toBytecode());
        fos.close();
        //
        return bytecodeProxy;
    }

    private InvokableInstruction toInstructionPe(String inst) throws Exception {
        CtClass mCtc = classPool.makeClass(InvokableInstruction.class.getName() + StringUtils.capitalize(inst + "_pe"));
        mCtc.addInterface(classPool.get(InvokableInstruction.class.getName()));
        mCtc.addConstructor(CtNewConstructor.defaultConstructor(mCtc));
        mCtc.addMethod(CtNewMethod.make("public void invoke(WscVM vm, String param, String param_extend) throws WscVMException { VmCpu." + inst + "(vm,param,param_extend);vm.stack.push(vm.eax.read());}", mCtc));
        Class pc = mCtc.toClass();
        InvokableInstruction bytecodeProxy = (InvokableInstruction) pc.newInstance();

        FileOutputStream fos = new FileOutputStream("target/" + pc.getSimpleName() + ".class");
        fos.write(mCtc.toBytecode());
        fos.close();
        //
        return bytecodeProxy;
    }

    public static void main(String[] args) {
        new InstMap();
    }
}
