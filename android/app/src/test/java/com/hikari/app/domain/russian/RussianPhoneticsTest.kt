package com.hikari.app.domain.russian

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class RussianPhoneticsTest {

    private fun tr(s: String) = RussianPhonetics.transliterateFlat(s)

    @Test
    fun grundwoerterMitAkanjeUndBetonung() {
        assertEquals("Priwjét!", tr("Прив+ет!"))
        assertEquals("Spassíba!", tr("Спас+ибо!"))
        assertEquals("Paká!", tr("Пок+а!"))
        assertEquals("Dóbraje útra!", tr("Д+оброе +утро!"))
        assertEquals("Charaschó.", tr("Хорош+о."))
        assertEquals("Njet.", tr("Нет."))
        assertEquals("Da.", tr("Да."))
    }

    @Test
    fun ausnahmenWerdenGesprochenGeschrieben() {
        assertEquals("Sdrástwujte!", tr("Здр+авствуйте!"))
        assertEquals("Schto?", tr("Что?"))
        assertEquals("Kanjéschna!", tr("Кон+ечно!"))
        assertEquals("ssiwódnja", tr("сег+одня"))
        assertEquals("Schto nówawa?", tr("Что н+ового?"))
        assertEquals("Nitschiwó nówawa.", tr("Ничег+о н+ового."))
        assertEquals("Kak jiwó sawút?", tr("Как ег+о зов+ут?"))
        assertEquals("kafé", tr("каф+е"))
    }

    @Test
    fun stimmangleichungUndAuslautverhaertung() {
        assertEquals("ftschirá", tr("вчер+а"))
        assertEquals("sáftra", tr("з+автра"))
        assertEquals("druk", tr("друг"))
        assertEquals("dwátzat'", tr("дв+адцать"))
        assertEquals("Idjót doscht'.", tr("Идёт дождь."))
        // Präposition verschmilzt: в vor stimmlosem Laut wird f
        assertEquals("F ssjem'.", tr("В семь."))
        assertEquals("Pajdjóm f kafé?", tr("Пойдём в каф+е?"))
        // vor stimmhaftem/sonorem Laut bleibt w
        assertEquals("Ja zhywú w Girmánii.", tr("Я жив+у в Герм+ании."))
    }

    @Test
    fun unbetonteVokaleUndWeicheKonsonanten() {
        assertEquals("tibjá", tr("теб+я"))
        assertEquals("ssistrá", tr("сестр+а"))
        assertEquals("jisýk", tr("яз+ык"))
        assertEquals("minjú", tr("мен+ю"))
        assertEquals("ssim'já", tr("семь+я"))
        assertEquals("djen'", tr("день"))
        assertEquals("panimájisch", tr("поним+аешь"))
    }

    @Test
    fun klitikaSindUnbetont() {
        assertEquals("Ja ni panimáju.", tr("Я не поним+аю."))
        assertEquals("da swidánija", tr("до свид+ания"))
        assertEquals("pa-rússki", tr("по-р+усски"))
        // betontes не zieht die Betonung vom folgenden Einsilber ab
        assertEquals("Njé sa schta.", tr("Н+е за что."))
        assertEquals("nikagdá njé byl", tr("никогд+а н+е был"))
    }

    @Test
    fun konjunktionChtoIstUnbetont() {
        assertEquals("Patamú schta éta intirjésna.", tr("Потом+у что +это интер+есно."))
        assertEquals("Ja dúmaju, schta éta charaschó.", tr("Я д+умаю, что +это хорош+о."))
        // Fragewort bleibt betont (einsilbig → nicht hervorgehoben, aber nicht reduziert)
        assertEquals("A schto ty ljúbisch djélat'?", tr("А что ты л+юбишь д+елать?"))
        assertEquals("Ótschin'", tr("+Очень"))
    }

    @Test
    fun verbendungTsjaKlingtWieZa() {
        assertEquals("nráwiza", tr("нр+авится"))
        assertEquals("sanimájischsja", tr("заним+аешься"))
    }

    @Test
    fun ssNurVorVokal() {
        assertEquals("rassíi", tr("Росс+ии").lowercase())
        assertEquals("klássna", tr("кл+ассно"))
        assertEquals("rússkij", tr("р+усский"))
        assertEquals("schot", tr("счёт"))
    }

    @Test
    fun betonterAbschnittIstMarkiert() {
        val spans = RussianPhonetics.transliterate("Спас+ибо")
        val stressed = spans.filter { it.stressed }
        assertEquals(1, stressed.size)
        assertEquals("i", stressed.single().text)
        assertEquals("Spassíba", spans.joinToString("") { if (it.stressed) "í" else it.text })
    }

    @Test
    fun anzeigeSetztAkut() {
        assertEquals("Приве́т!", RussianPhonetics.display("Прив+ет!"))
        assertEquals("Всё хорошо́.", RussianPhonetics.display("Всё хорош+о."))
        assertEquals("Привет!", RussianPhonetics.plain("Прив+ет!"))
    }

    @Test
    fun buchstabenLupe() {
        val pairs = RussianPhonetics.letterPairs("Да, нет")
        assertEquals(listOf('Д' to "d", 'а' to "a", null, 'н' to "n", 'е' to "je", 'т' to "t"), pairs)
        assertTrue(RussianPhonetics.LETTER_SOUNDS.size == 33)
    }
}
