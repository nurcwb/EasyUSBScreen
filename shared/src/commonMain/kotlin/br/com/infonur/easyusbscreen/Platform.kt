package br.com.infonur.easyusbscreen

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform