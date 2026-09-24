package app.luxctrl

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

class ColorDiscMathTest {
    @Test fun hueRingMatchesReferenceOrientationAndWraps() {
        assertEquals(0f,ColorDiscMath.hue(1f,0f),.001f)
        assertEquals(90f,ColorDiscMath.hue(0f,-1f),.001f)
        assertEquals(180f,ColorDiscMath.hue(-1f,0f),.001f)
        assertEquals(270f,ColorDiscMath.hue(0f,1f),.001f)
        assertTrue(ColorDiscMath.hue(1f,.001f)>359f)
        assertTrue(ColorDiscMath.hue(1f,-.001f)<1f)
    }
    @Test fun discHasWhiteAndPureHueAboveBlack() {
        val d=sqrt(.5f)
        val white=ColorDiscMath.tone(-d,-d)
        val color=ColorDiscMath.tone(d,-d)
        val black=ColorDiscMath.tone(0f,1f)
        assertEquals(0f,white.saturation,.001f); assertEquals(1f,white.brightness,.001f)
        assertEquals(1f,color.saturation,.001f); assertEquals(1f,color.brightness,.001f)
        assertEquals(0f,black.brightness,.001f)
        val center=ColorDiscMath.tone(0f,0f)
        assertEquals(.5f,center.saturation,.001f); assertEquals(.5f,center.brightness,.001f)
    }
    @Test fun selectionMarkerRoundTripsAcrossEntireColorRange() {
        for(s in 0..20) for(v in 0..20) {
            val point=ColorDiscMath.point(s/20f,v/20f)
            assertTrue(point.x*point.x+point.y*point.y<=1.00001f)
            val tone=ColorDiscMath.tone(point.x,point.y)
            assertEquals(s/20f,tone.saturation,.0003f)
            assertEquals(v/20f,tone.brightness,.0003f)
        }
    }
    @Test fun DraggingBeyondDiscClampsAtEdgeWithoutJumpingHue() {
        val edge=ColorDiscMath.tone(.6f,-.8f)
        val outside=ColorDiscMath.tone(6f,-8f)
        assertEquals(edge.saturation,outside.saturation,.001f)
        assertEquals(edge.brightness,outside.brightness,.001f)
        assertEquals(0f,ColorDiscMath.tone(0f,10f).brightness,.001f)
    }
}
