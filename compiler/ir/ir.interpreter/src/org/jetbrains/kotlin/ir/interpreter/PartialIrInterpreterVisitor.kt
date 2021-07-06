/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.interpreter

import org.jetbrains.kotlin.ir.IrBuiltIns
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.interpreter.checker.EvaluationMode
import org.jetbrains.kotlin.ir.interpreter.stack.CallStack
import org.jetbrains.kotlin.ir.interpreter.state.State
import org.jetbrains.kotlin.ir.interpreter.state.asBoolean
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid

internal class Evaluator(val irBuiltIns: IrBuiltIns, val transformer: IrElementTransformerVoid) {
    private val environment = IrInterpreterEnvironment(irBuiltIns)
    internal val callStack: CallStack
        get() = environment.callStack
    private val interpreter = IrInterpreter(environment, emptyMap())

    internal fun evaluate(irExpression: IrExpression, args: List<State> = emptyList(), interpretOnly: Boolean = true) {
        callStack.safeExecute {
            interpreter.interpret(
                {
                    this.newSubFrame(irExpression)
                    this.pushInstruction(if (interpretOnly) SimpleInstruction(irExpression) else CompoundInstruction(irExpression))
                    args.reversed().forEach { this.pushState(it) }
                },
                { this.dropSubFrame() }
            )
        }
    }

    fun interpret(block: IrReturnableBlock): IrElement {
        callStack.newFrame(block)
        block.transformChildren(transformer, null)
        callStack.dropFrame()
        return block
    }

    private fun IrFunctionAccessExpression.getExpectedArgumentsCount(): Int {
        val dispatch = dispatchReceiver?.let { 1 } ?: 0
        val extension = extensionReceiver?.let { 1 } ?: 0
        return dispatch + extension + valueArgumentsCount
    }

    fun evalIrReturnValue(expression: IrReturn): State? {
        expression.value = expression.value.transform(transformer, null)
        return callStack.tryToPopState()
    }

    fun fallbackIrReturn(expression: IrReturn, value: State?): IrReturn {
        return expression
    }

    fun evalIrCallDispatchReceiver(expression: IrCall): State? {
        expression.dispatchReceiver = expression.dispatchReceiver?.transform(transformer, null)
        return callStack.tryToPopState()
    }

    fun evalIrCallExtensionReceiver(expression: IrCall): State? {
        expression.extensionReceiver = expression.extensionReceiver?.transform(transformer, null)
        return callStack.tryToPopState()
    }

    fun evalIrCallArgs(expression: IrCall): List<State?> {
        (0 until expression.valueArgumentsCount).forEach {
            expression.putValueArgument(it, expression.getValueArgument(it)?.transform(transformer, null))
        }
        val args = mutableListOf<State?>()
        (0 until expression.valueArgumentsCount).forEach {
            args += callStack.tryToPopState()
        }
        return args
    }

    fun fallbackIrCall(expression: IrCall, dispatchReceiver: State?, extensionReceiver: State?, args: List<State?>): IrCall {
        val actualArgs = listOf(dispatchReceiver, extensionReceiver, *args.toTypedArray()).filterNotNull()
        if (actualArgs.size != expression.getExpectedArgumentsCount()) return expression

        val owner = expression.symbol.owner
        if (EvaluationMode.ONLY_BUILTINS.canEvaluateFunction(owner, expression) || EvaluationMode.WITH_ANNOTATIONS.canEvaluateFunction(owner, expression)) {
            evaluate(expression, actualArgs, interpretOnly = true)
            // TODO if result is Primitive -> return const
            return expression
        }
        return expression
    }

    fun evalIrBlock(expression: IrBlock) {
        expression.transformChildren(transformer, null)
    }

    fun fallbackIrBlock(expression: IrBlock): IrExpression {
        callStack.newSubFrame(expression)
        evalIrBlock(expression)
        callStack.dropSubFrame()
        return expression
    }

    fun evalIrWhenConditions(expression: IrWhen): List<State?> {
        return expression.branches.map {
            it.condition = it.condition.transform(transformer, null)
            callStack.tryToPopState()
        }
    }

    fun fallbackIrWhen(expression: IrWhen, conditions: List<State?>): IrExpression {
        for ((i, condition) in conditions.withIndex()) {
            when {
                condition == null -> {
                    (i until expression.branches.size).forEach {
                        expression.branches[it].result = expression.branches[it].result.transform(transformer, null)
                    }
                    // TODO collect all mutable vars and remove them + rollback all changes
                    return expression
                }
                condition.asBoolean() -> return expression.branches[i].result.transform(transformer, null)
                else -> {
                    expression.branches[i].result = expression.branches[i].result.transform(transformer, null)
                    // TODO rollback all changes
                }
            }
        }
        return expression
    }

    fun evalIrSetValue(expression: IrSetValue): State? {
        expression.value = expression.value.transform(transformer, null)
        return callStack.tryToPopState()
    }

    fun fallbackIrSetValue(expression: IrSetValue, value: State?): IrExpression {
        if (value == null) {
            // TODO remove from stack
            return expression
        }
        evaluate(expression, listOf(value))
        return expression
    }

    fun evalIrGetValue(expression: IrGetValue): State? {
        // TODO evaluate can throw exception, how to avoid?
        evaluate(expression, interpretOnly = false)
        return callStack.tryToPopState()
    }

    fun fallbackIrGetValue(expression: IrGetValue, value: State?): IrExpression {
        value?.let { callStack.pushState(it) }
        return expression
    }

    fun <T> evalIrConst(expression: IrConst<T>): State? {
        evaluate(expression)
        return callStack.tryToPopState()
    }

    fun <T> fallbackIrConst(expression: IrConst<T>, value: State?): IrExpression {
        value?.let { callStack.pushState(it) }
        return expression
    }

    fun evalIrVariable(declaration: IrVariable): State? {
        declaration.initializer = declaration.initializer?.transform(transformer, null)
        return callStack.tryToPopState()
    }

    fun fallbackIrVariable(declaration: IrVariable, value: State?): IrStatement {
        if (declaration.initializer == null) {
            callStack.storeState(declaration.symbol, null)
        } else {
            value?.let { callStack.storeState(declaration.symbol, it) }
        }
        return declaration
    }
}

// TODO
// 1. cache.
// there can be many `lowerings` that are using compile time information.
// but this information will be the same every time, so there is no need to reinterpret all code
internal class PartialIrInterpreterVisitor(val irBuiltIns: IrBuiltIns) : IrElementTransformerVoid() {
    protected val evaluator = Evaluator(irBuiltIns, this)

    fun interpret(block: IrReturnableBlock): IrElement {
        return evaluator.interpret(block)
    }

    override fun visitReturn(expression: IrReturn): IrExpression {
        return evaluator.fallbackIrReturn(
            expression,
            evaluator.evalIrReturnValue(expression)
        )
    }

    override fun visitCall(expression: IrCall): IrExpression {
        return evaluator.fallbackIrCall(
            expression,
            evaluator.evalIrCallDispatchReceiver(expression),
            evaluator.evalIrCallExtensionReceiver(expression),
            evaluator.evalIrCallArgs(expression)
        )
    }

    override fun visitBlock(expression: IrBlock): IrExpression {
        return evaluator.fallbackIrBlock(expression)
    }

    override fun visitWhen(expression: IrWhen): IrExpression {
        return evaluator.fallbackIrWhen(
            expression,
            evaluator.evalIrWhenConditions(expression)
        )
    }

    override fun visitSetValue(expression: IrSetValue): IrExpression {
        return evaluator.fallbackIrSetValue(
            expression,
            evaluator.evalIrSetValue(expression)
        )
    }

    override fun visitGetValue(expression: IrGetValue): IrExpression {
        return evaluator.fallbackIrGetValue(
            expression,
            evaluator.evalIrGetValue(expression)
        )
    }

    override fun <T> visitConst(expression: IrConst<T>): IrExpression {
        return evaluator.fallbackIrConst(
            expression,
            evaluator.evalIrConst(expression)
        )
    }

    override fun visitVariable(declaration: IrVariable): IrStatement {
        return evaluator.fallbackIrVariable(
            declaration,
            evaluator.evalIrVariable(declaration)
        )
    }
}