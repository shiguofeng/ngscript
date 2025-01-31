/*
 * Copyright 2021 wssccc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ngscript.runtime.opcache;

import org.ngscript.compiler.Instruction;
import org.ngscript.runtime.InvokableInstruction;
import org.ngscript.runtime.VirtualMachine;
import org.ngscript.runtime.VmRuntimeException;

public class OpBinding extends Instruction {

    InvokableInstruction invokableInstruction;

    public OpBinding(Instruction instruction, InvokableInstruction invokableInstruction) {
        super(instruction.op, instruction.param, instruction.paramExtended);
        this.invokableInstruction = invokableInstruction;
    }

    public void invoke(VirtualMachine vm) throws VmRuntimeException {
        invokableInstruction.invoke(vm, param, paramExtended);
    }
}
