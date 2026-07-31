package cn.ahlib.reservation.data

import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import okio.ByteString.Companion.toByteString

class PasswordCipher {
    fun encrypt(password: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val key = SecretKeySpec(KEY.toByteArray(StandardCharsets.UTF_8), ALGORITHM)
        val iv = IvParameterSpec(IV.toByteArray(StandardCharsets.UTF_8))
        cipher.init(Cipher.ENCRYPT_MODE, key, iv)
        return cipher.doFinal(password.toByteArray(StandardCharsets.UTF_8))
            .toByteString()
            .base64()
    }

    private companion object {
        const val ALGORITHM = "AES"
        const val TRANSFORMATION = "AES/CBC/PKCS5Padding"
        const val KEY = "asefcxtsgckzxKey"
        const val IV = "asefcxtsgckzx_Iv"
    }
}
