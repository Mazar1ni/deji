package com.github.mazar1ni.deji

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class Setup(val packages: Array<String>)
