package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.BoardGeometry
import com.example.model.Puck
import com.example.model.PuckType
import com.example.model.Vector2D
import com.example.physics.PhysicsCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
        assertEquals("Carrom Aim", appName)
    }

    @Test
    fun `test physics trajectory calculation`() {
        val board = BoardGeometry()
        val calculator = PhysicsCalculator(board)

        val strikerPos = Vector2D(board.centerX, board.bottomBaselineY)
        val aimDir = Vector2D(0f, -1f)
        val pucks = listOf(
            Puck(1, Vector2D(board.centerX, board.centerY), type = PuckType.QUEEN)
        )

        val result = calculator.calculateTrajectory(
            strikerPos = strikerPos,
            aimDirection = aimDir,
            pucks = pucks
        )

        assertNotNull(result.contactGhostPuckPos)
        assertEquals(1, result.targetPuck?.id)
        assertTrue(result.strikerPath.isNotEmpty())
        assertTrue(result.targetPuckPath.isNotEmpty())
    }

    @Test
    fun `test cushion reflection physics`() {
        val board = BoardGeometry()
        val calculator = PhysicsCalculator(board)

        val origin = Vector2D(500f, 500f)
        val dir = Vector2D(1f, -1f).normalized()

        val cushionHit = calculator.raycastCushion(origin, dir, radius = 24f)
        assertNotNull(cushionHit)
        assertEquals(0f, cushionHit!!.normal.x, 0.01f)
        assertEquals(1f, cushionHit.normal.y, 0.01f)

        val reflected = dir.reflect(cushionHit.normal)
        assertEquals(dir.x, reflected.x, 0.01f)
        assertEquals(-dir.y, reflected.y, 0.01f)
    }
}
