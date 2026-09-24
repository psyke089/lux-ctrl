package app.luxctrl

import org.junit.Assert.*
import org.junit.Test

class ControlMathTest {
    @Test fun onlyPrivateNumericBridgeAddressesAreAccepted() {
        listOf("192.168.1.20","10.0.0.1","172.16.0.1","172.31.255.254","169.254.1.2").forEach { assertTrue(it,BridgeConfig.isLocalIpv4(it)) }
        listOf("8.8.8.8","127.0.0.1","172.15.0.1","172.32.0.1","192.168.999.1","192.168.01.2","192.168.1","hue.local","192.168.1.2/path","192.168.1.2:80","192.168.1.2\n","-1.2.3.4","", "192.168.1.256").forEach { assertFalse(it,BridgeConfig.isLocalIpv4(it)) }
    }
    @Test fun keysCannotInjectUrlPaths() {
        assertNull(BridgeConfig("192.168.1.1","test-key_1234567890").validate())
        assertNotNull(BridgeConfig("192.168.1.1","secret/../../config").validate())
        assertNotNull(BridgeConfig("192.168.1.1","").validate())
    }
    @Test fun wheelRunsClockwiseFromTwelveOClock() {
        assertEquals(0,WheelMath.slice(0f,-100f,6))
        assertEquals(1,WheelMath.slice(100f,0f,6))
        assertEquals(3,WheelMath.slice(0f,100f,6))
        assertEquals(4,WheelMath.slice(-100f,0f,6))
        assertEquals(-1,WheelMath.slice(0f,0f,0))
    }
    @Test fun radialBrightnessClampsAndSupportsOff() {
        assertEquals(227,WheelMath.brightness(100,50f,100f))
        assertEquals(0,WheelMath.brightness(100,-50f,100f))
        assertEquals(254,WheelMath.brightness(200,1000f,100f))
        assertEquals(0,WheelMath.brightness(200,-1000f,100f))
        assertEquals(130,WheelMath.brightness(130,0f,100f))
    }
    @Test fun hsvPrimariesAndUnsaturatedWhite() {
        assertEquals(0xffff0000.toInt(),HueColor.fromHsv(0f,1f))
        assertEquals(0xff00ff00.toInt(),HueColor.fromHsv(120f,1f))
        assertEquals(0xff0000ff.toInt(),HueColor.fromHsv(240f,1f))
        assertEquals(0xffffffff.toInt(),HueColor.fromHsv(180f,0f))
        assertEquals(HueColor.fromHsv(0f,1f),HueColor.fromHsv(360f,1f))
    }
    @Test fun xyConversionProducesValidColorsAtGamutExtremes() {
        val red=HueColor.fromXy(.675,.322)
        assertTrue(((red ushr 16) and 255) > ((red ushr 8) and 255))
        val green=HueColor.fromXy(.17,.7)
        assertTrue(((green ushr 8) and 255) > ((green ushr 16) and 255))
        val blue=HueColor.fromXy(.15,.06)
        assertTrue((blue and 255) > ((blue ushr 8) and 255))
        assertEquals(0xffdce9ed.toInt(),HueColor.fromXy(0.0,0.0))
        assertEquals(0xffdce9ed.toInt(),HueColor.fromXy(Double.NaN,.5))
    }
    @Test fun warmWhiteHasLessBlueThanCoolWhite() {
        assertTrue((HueColor.fromMired(500) and 255) < (HueColor.fromMired(153) and 255))
    }
}
