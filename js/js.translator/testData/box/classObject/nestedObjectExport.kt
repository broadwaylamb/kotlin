// SKIP_DCE_DRIVEN
// See KT-43783

var log = ""

@JsExport
class Abc {
    init {
        log += "Constructor OK\n"
    }

    companion object AbcCompanion {
        fun xyz() {
            log += "Companion object function OK\n"
        }

        val prop: String
            get() = "Companion object property OK"
    }
}

@JsExport
sealed class MyEnum(val name: String) {
    object A: MyEnum("A")
    object B: MyEnum("B")
    object C: MyEnum("C")
}

fun getPackage() = js("return JS_TESTS")

fun box(): String {
    assertEquals("", log)
    js("new JS_TESTS.Abc()")
    assertEquals("Constructor OK\n", log)

    val d = getPackage()

    val companionObject = d.Abc.AbcCompanion
    companionObject.xyz()
    assertEquals("Constructor OK\nCompanion object function OK\n", log)
    assertEquals("Companion object property OK", companionObject.prop)

    assertEquals(d.MyEnum.A.name, "A")
    assertEquals(d.MyEnum.B.name, "B")
    assertEquals(d.MyEnum.C.name, "C")
    return "OK"
}
