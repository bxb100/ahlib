package cn.ahlib.reservation.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PasswordCipherTest {
    private val cipher = PasswordCipher()

    @Test
    fun encrypt_matchesCryptoJsCompatibleVector() {
        assertEquals("XFtW7PydnO5Q4iMsDMUcyA==", cipher.encrypt("password"))
    }

    @Test
    fun encrypt_appliesFullBlockPaddingToEmptyPassword() {
        assertEquals("59SnmRNdPiuqC7+5gf9Keg==", cipher.encrypt(""))
    }
}
