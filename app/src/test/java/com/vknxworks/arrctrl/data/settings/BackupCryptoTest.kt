package com.vknxworks.arrctrl.data.settings

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupCryptoTest {
    private val plain = "{\"format\":\"arrctrl-config\"}".toByteArray()

    @Test fun opensWithTheSamePassword() {
        val file = BackupCrypto.seal(plain, "correct horse".toCharArray())
        assertTrue(BackupCrypto.looksLikeBackup(file))
        assertFalse(file.contains("arrctrl-config"))
        assertArrayEquals(plain, BackupCrypto.open(file, "correct horse".toCharArray()))
        // Line endings and blank lines added by a mail or cloud app don't matter.
        assertArrayEquals(plain, BackupCrypto.open("\r\n" + file.replace("\n", "\r\n") + "\r\n", "correct horse".toCharArray()))
    }

    @Test fun twoBackupsOfTheSameContentDiffer() {
        assertNotEquals(BackupCrypto.seal(plain, "password1".toCharArray()), BackupCrypto.seal(plain, "password1".toCharArray()))
    }

    @Test fun aWrongPasswordOrAChangedFileDoesNotOpen() {
        val file = BackupCrypto.seal(plain, "correct horse".toCharArray())
        try {
            BackupCrypto.open(file, "wrong horse".toCharArray())
            fail("opened with the wrong password")
        } catch (e: BackupCrypto.WrongPassword) {
        }
        val lines = file.trim().lines().toMutableList()
        val body = lines[4]
        lines[4] = (if (body[10] == 'A') 'B' else 'A').let { body.substring(0, 10) + it + body.substring(11) }
        try {
            BackupCrypto.open(lines.joinToString("\n"), "correct horse".toCharArray())
            fail("opened a changed file")
        } catch (e: BackupCrypto.WrongPassword) {
        }
    }

    @Test fun otherFilesAreNotBackups() {
        val file = BackupCrypto.seal(plain, "correct horse".toCharArray())
        val lines = file.trim().lines()
        val others = listOf(
            "",
            "hello",
            "{\"arrctrl\":1}",
            lines.take(4).joinToString("\n"),
            lines.toMutableList().also { it[1] = "5" }.joinToString("\n"),
            lines.toMutableList().also { it[1] = "999999999" }.joinToString("\n"),
            lines.toMutableList().also { it[2] = "AAAA" }.joinToString("\n"),
            lines.toMutableList().also { it[3] = "not base64 !!" }.joinToString("\n"),
        )
        for (text in others) {
            try {
                BackupCrypto.open(text, "correct horse".toCharArray())
                fail("accepted: $text")
            } catch (e: BackupCrypto.NotABackup) {
            }
        }
    }
}
