package com.phasmidsoftware.util

import com.phasmidsoftware.util.PasswordStrength.substrings
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

class PasswordStrengthSpec extends AnyFlatSpec with should.Matchers {

    behavior of "PasswordStrength"

    it should "apply" in {
        PasswordStrength("") shouldBe 0
        PasswordStrength("a") shouldBe 1
        PasswordStrength("ab") shouldBe 4
        PasswordStrength("good") shouldBe 15
    }

    it should "substrings" in {
        substrings(Set.empty, Nil) shouldBe Set()
        substrings(Set.empty, List("a")) shouldBe Set("a")
        substrings(Set.empty, List("ab")) shouldBe Set("ab", "a", "b")
        substrings(Set.empty, List("good")) shouldBe Set("good", "g", "ood", "o", "od", "go", "d", "goo", "oo")
    }

}
