package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Goblin", appName)
  }

  @Test
  fun `test magnetic delta calculation`() {
    val reading = com.example.domain.model.MagneticReading(
        x = 10f, y = 20f, z = 30f,
        baselineX = 10f, baselineY = 20f, baselineZ = 25f,
        deltaX = 0f, deltaY = 0f, deltaZ = 5f,
        deltaMagnitude = 5f
    )
    assertEquals(5f, reading.deltaMagnitude, 0.01f)
    assertEquals(com.example.domain.model.MagneticDirection.SCREEN_FACE, reading.dominantDirection)
  }
}
