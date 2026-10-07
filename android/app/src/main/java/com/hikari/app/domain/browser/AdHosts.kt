package com.hikari.app.domain.browser

/**
 * Zentrale Liste bekannter Werbe-/Tracking-Hosts.
 *
 * Sniffer und Request-Blocker teilen sich diese eine Liste:
 *  - Der Sniffer verwirft Media-Funde von diesen Hosts (der Werbe-Preroll
 *    lädt vor dem echten Video und würde sonst als "das Video der Seite"
 *    gelten).
 *  - Der WebView-Client blockiert ihre Subrequests ganz und ignoriert
 *    Hauptframe-Redirects dorthin — sonst leitet eine Ad das Hauptfenster
 *    um, und der mitgelesene Stream wird im Korb der falschen Seite
 *    zugeordnet (beobachtet bei s.lazada.co.th/s.…).
 */
object AdHosts {

    val HOSTS = listOf(
        // Google-Werbung & Preroll
        "doubleclick.net",
        "googleadservices.com",
        "googlesyndication.com",
        "imasdk.googleapis.com",
        // Vermarkter & Messung
        "adsystem.com",
        "adnxs.com",
        "scorecardresearch.com",
        "moatads.com",
        "outbrain.com",
        "taboola.com",
        "adskeeper.com",
        // Pop-up- und Click-through-Netze
        "popads.net",
        "popcash.net",
        "exoclick.com",
        "adsco.re",
        // Gemessen auf serienstream.to-Folgenseiten (Pop-under/Click-Netze)
        "255md.com",
        "jhnwr.com",
        "al5sm.com",
        "propellerads.com",
        "propellerclick.com",
        "onclickads.net",
        "onclkds.com",
        "adsterra.com",
        "adsterratools.com",
        "highperformancegate.com",
        "highcpmgate.com",
        "juicyads.com",
        "clickadu.com",
        "hilltopads.net",
        "hilltopads.com",
        "trafficjunky.net",
        "trafficfactory.biz",
        "exosrv.com",
        "realsrv.com",
        "tsyndicate.com",
        "a-ads.com",
        "ad-maven.com",
        "admaven.com",
        "richads.com",
        "monetag.com",
        "profitablecpmrate.com",
        "profitableratecpm.com",
        "effectivegatecpm.com",
        "go.ad2up.com",
        "ad2up.com",
        "bidvertiser.com",
        "revcontent.com",
        "mgid.com",
        "yllix.com",
        "mc.yandex.ru",
        "an.yandex.ru",
        "criteo.com",
        "criteo.net",
        "rubiconproject.com",
        "pubmatic.com",
        "openx.net",
        "casalemedia.com",
        "smartadserver.com",
        "amazon-adsystem.com",
        "facebook.net",
        "hotjar.com",
        "quantserve.com",
        "adform.net",
        "teads.tv",
        "2mdn.net",
        "googletagservices.com",
        "google-analytics.com",
        // Kurzlink-Tracker der Lazada-Ads — leiten das Hauptfenster um
        "s.lazada.co.th",
        "s.lazada.com",
        "c.lazada.co.th",
    )

    /** true, wenn [host] ein bekannter Ad-/Tracker-Host ist oder darunter liegt. */
    fun isAdHost(host: String?): Boolean {
        val h = host?.lowercase() ?: return false
        return HOSTS.any { h == it || h.endsWith(".$it") }
    }

    /** true, wenn [url] auf einen bekannten Ad-/Tracker-Host zeigt. */
    fun isAdUrl(url: String): Boolean =
        isAdHost(runCatching { java.net.URI(url).host }.getOrNull())
}
