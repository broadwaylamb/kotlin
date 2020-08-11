// WITH_RUNTIME
// WITH_COROUTINES
// TARGET_BACKEND: JVM
// IGNORE_BACKEND: JVM
// IGNORE_BACKEND_FIR: JVM_IR
// !JVM_IR_BACKEND

import helpers.*
import kotlin.coroutines.*

fun interface Action {
    suspend fun run()
}
suspend fun runAction(a: Action) {
    a.run()
}
fun builder(c: suspend () -> Unit) {
    c.startCoroutine(EmptyContinuation)
}
fun box(): String {
    var res = "FAIL"
    builder {
        runAction {
            res = "OK"
        }
    }
    return res
}