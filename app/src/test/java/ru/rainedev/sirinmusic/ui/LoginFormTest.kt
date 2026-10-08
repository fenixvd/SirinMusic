package ru.rainedev.sirinmusic.ui

import org.junit.Assert.*
import org.junit.Test
import ru.rainedev.sirinmusic.data.ConnectLink

class LoginFormTest {
    private val typed = LoginForm("http://a.local:8787", "token-for-server-a")

    @Test fun addressOnlyQrClearsTheTokenAndDoesNotSignIn() {
        val result = typed.withLink(ConnectLink.parse("http://b.local:8787")!!)
        assertEquals(LoginForm("http://b.local:8787", ""), result.form)
        assertFalse(result.signIn)
    }

    @Test fun emptyTokenInTheLinkAlsoClearsTheField() {
        val result = typed.withLink(ConnectLink.parse("musik://connect?url=http%3A%2F%2Fb.local%3A8787&token=")!!)
        assertEquals("", result.form.token)
        assertFalse(result.signIn)
    }

    @Test fun linkWithTokenFillsBothFieldsAndSignsIn() {
        val result = typed.withLink(ConnectLink.parse("musik://connect?url=http%3A%2F%2Fb.local%3A8787&token=t-b")!!)
        assertEquals(LoginForm("http://b.local:8787", "t-b"), result.form)
        assertTrue(result.signIn)
    }
}
