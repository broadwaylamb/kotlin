package lol

class B() {
    operator fun plusAssign(other : B) : String {
        return "s"
    }
    operator fun minusAssign(other : B) : String {
        return "s"
    }
    operator fun remAssign(other : B) : String {
        return "s"
    }
    operator fun divAssign(other : B) : String {
        return "s"
    }
    operator fun timesAssign(other : B) : String {
        return "s"
    }
}

fun main() {
    var c = B()
    c <!ASSIGNMENT_OPERATOR_SHOULD_RETURN_UNIT!>+=<!> B()
    c <!ASSIGNMENT_OPERATOR_SHOULD_RETURN_UNIT!>*=<!> B()
    c <!ASSIGNMENT_OPERATOR_SHOULD_RETURN_UNIT!>/=<!> B()
    c <!ASSIGNMENT_OPERATOR_SHOULD_RETURN_UNIT!>-=<!> B()
    c <!ASSIGNMENT_OPERATOR_SHOULD_RETURN_UNIT!>%=<!> B()
}
