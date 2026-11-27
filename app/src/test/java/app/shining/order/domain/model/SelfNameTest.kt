package app.shining.order.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** Samad's real cafeteria names (2026-11) and the short ones the app shows. */
class SelfNameTest {

    @Test
    fun `both سلف مرکزی selfs are سلف`() {
        assertEquals("سلف", shortSelfName("مرکزی - سلف دانشجویان آقا"))
        assertEquals("سلف", shortSelfName("مرکزی - سلف دانشجویان خانم"))
    }

    @Test
    fun `کاله drops the take-away note and the main campus, and keeps the north campus`() {
        assertEquals("کاله", shortSelfName("کاله پردیس مرکزی(بیرون بر)"))
        assertEquals("کاله شمالی", shortSelfName("کاله پردیس شمالی(بیرون بر)"))
    }

    @Test
    fun `short names stay as they are`() {
        assertEquals("کلانا", shortSelfName("کلانا"))
        assertEquals("فست‌فود شریف", shortSelfName("فست‌فود شریف"))
        assertEquals("یونی‌فود", shortSelfName("یونی‌فود"))
    }

    @Test
    fun `a dorm keeps خوابگاه`() {
        assertEquals("خوابگاه طرشت 3", shortSelfName("خوابگاه - طرشت 3"))
        assertEquals("خوابگاه آزادی", shortSelfName("خوابگاه - آزادی"))
    }

    @Test
    fun `a name that would come out empty is kept`() {
        assertEquals("(بیرون بر)", shortSelfName("(بیرون بر)"))
    }

    @Test
    fun `the vendor in brackets goes when the place tag already says it`() {
        assertEquals("ساندویچ مرغ پستو", foodNameWithoutPlace("ساندویچ مرغ پستو (کاله)", "کاله"))
        assertEquals("ساندویچ ژامبون مرغ", foodNameWithoutPlace("ساندویچ ژامبون مرغ (کاله)", "کاله شمالی"))
        assertEquals("سالاد آسیایی", foodNameWithoutPlace("سالاد آسیایی(کلانا)", "کلانا"))
        // Samad writes the vendor with a space in the food and a half-space in the cafeteria.
        assertEquals("ساندویچ نرمال بیکن مرغ", foodNameWithoutPlace("ساندویچ نرمال بیکن مرغ(کلین فود)", "کلین‌فود"))
        assertEquals("ساندویچ ژامبون مرغ", foodNameWithoutPlace("ساندویچ ژامبون مرغ(فست فود شریف)", "فست‌فود شریف"))
        assertEquals("ساندویچ بیکن ویژه مرغ", foodNameWithoutPlace("ساندویچ بیکن ویژه مرغ(یونی فود)", "یونی‌فود"))
    }

    @Test
    fun `a bracket that says something new stays`() {
        // A کاله dish sold at سلف: the tag says سلف, so "(کاله)" is news.
        assertEquals("سینی ماکارونی و ناگت (کاله)", foodNameWithoutPlace("سینی ماکارونی و ناگت (کاله)", "سلف"))
        assertEquals("ساندویچ فیله سوخاری(آکیم)", foodNameWithoutPlace("ساندویچ فیله سوخاری(آکیم)", "رستوران آزاد"))
        assertEquals("خوراک فلافل", foodNameWithoutPlace("خوراک فلافل", "سلف"))
    }

    @Test
    fun `with no place tag the name stays whole`() {
        assertEquals("ساندویچ مرغ پستو (کاله)", foodNameWithoutPlace("ساندویچ مرغ پستو (کاله)", null))
    }
}
