package com.cprint.app.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubUpdateManagerTest {
    @Test fun `recognizes newer semantic versions`() {
        assertTrue(GitHubUpdateManager.isNewerVersion("v1.2.0", "1.1.9"))
        assertTrue(GitHubUpdateManager.isNewerVersion("1.0.1", "1.0.0"))
        assertFalse(GitHubUpdateManager.isNewerVersion("1.0.0", "1.0.0"))
        assertFalse(GitHubUpdateManager.isNewerVersion("1.9.9", "2.0.0"))
    }
}
