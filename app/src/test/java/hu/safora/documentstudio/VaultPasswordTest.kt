package hu.safora.documentstudio

import org.junit.Assert.*
import org.junit.Test

class VaultPasswordTest {
    @Test fun correctPasswordIsAcceptedAndWrongPasswordRejected(){
        val record=VaultPasswords.create("secret-123".toCharArray())
        assertTrue(VaultPasswords.verify("secret-123".toCharArray(),record))
        assertFalse(VaultPasswords.verify("secret-124".toCharArray(),record))
    }
    @Test fun saltAndVerifierAreDifferentForEachVault(){
        val a=VaultPasswords.create("secret-123".toCharArray());val b=VaultPasswords.create("secret-123".toCharArray())
        assertNotEquals(a.salt,b.salt);assertNotEquals(a.verifier,b.verifier)
        assertFalse(a.toString().contains("secret-123"))
    }
    @Test fun unicodePasswordWorks(){
        val p="Árvíztűrő-2026".toCharArray();val r=VaultPasswords.create(p)
        assertTrue(VaultPasswords.verify(p,r));assertFalse(VaultPasswords.verify("Arvizturo-2026".toCharArray(),r))
    }
    @Test(expected=IllegalArgumentException::class)fun rejectsShortPassword(){VaultPasswords.create("12345".toCharArray())}
    @Test fun damagedVerifierFailsClosed(){assertFalse(VaultPasswords.verify("secret123".toCharArray(),VaultPassword("invalid!","!")))}
    @Test fun unsupportedWorkFactorFailsClosed(){assertFalse(VaultPasswords.verify("secret123".toCharArray(),VaultPassword("AA==","AA==",1)))}
}
