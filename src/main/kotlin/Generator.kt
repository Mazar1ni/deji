package com.github.mazar1ni.deji

import com.google.auto.service.AutoService
import com.squareup.kotlinpoet.*
import java.io.File
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.ElementKind
import javax.lang.model.element.TypeElement

@AutoService(Processor::class)
class Generator : AbstractProcessor() {

    companion object {
        const val KAPT_KOTLIN_GENERATED_OPTION_NAME = "kapt.kotlin.generated"
        const val PACKAGE_NAME = "com.github.mazar1ni.deji"
        const val FILE_NAME = "GeneratedSingleton"
        const val ROOT_PACKAGE = "rootPackage"
        const val INIT_FUNC = "<init>"
        const val BUILD_CONFIG_PACKAGE = ".BuildConfig"
        const val SET_FUNC_PREFIX = "set"
        var objBuilder: TypeSpec.Builder? = null
        var funBuilder: FunSpec.Builder? = null
        var rootPackage = PACKAGE_NAME
        val stringClassName = ClassName("kotlin", "String")
    }

    override fun getSupportedSourceVersion(): SourceVersion {
        return SourceVersion.latestSupported()
    }

    override fun getSupportedAnnotationTypes(): MutableSet<String> {
        return mutableSetOf(Singleton::class.java.name, Setup::class.java.name, SingletonRoom::class.java.name)
    }

    override fun process(annotations: MutableSet<out TypeElement>?, roundEnv: RoundEnvironment?): Boolean {

        //processingEnv.messager.printMessage(Diagnostic.Kind.WARNING, "process call")

        val elementsWithDISingleton = roundEnv?.getElementsAnnotatedWith(Singleton::class.java)?.toSet()
        val elementsWithDISetup = roundEnv?.getElementsAnnotatedWith(Setup::class.java)?.toSet()
        val elementsWithDISingletonRoom = roundEnv?.getElementsAnnotatedWith(SingletonRoom::class.java)?.toSet()

        if ((elementsWithDISingleton == null || elementsWithDISingleton.isEmpty())
            && (elementsWithDISetup == null || elementsWithDISetup.isEmpty())
            && (elementsWithDISingletonRoom == null || elementsWithDISingletonRoom.isEmpty())
            && roundEnv?.processingOver() == false
        ) {
            return true
        }

        if (objBuilder == null) objBuilder = TypeSpec.objectBuilder(FILE_NAME)

        elementsWithDISingleton?.forEach {
            val className = ClassName.bestGuess(it.asType().toString())

            val typeElement = processingEnv.elementUtils.getTypeElement(it.asType().toString())
            processingEnv.elementUtils.getAllMembers(typeElement).forEach { el ->
                if (el.annotationMirrors.find { ann -> ann.annotationType.toString() == Inject::class.java.name } != null) {
                    objBuilder!!.addFunction(FunSpec.builder("$SET_FUNC_PREFIX${
                        it.simpleName.toString().replaceFirstChar { word -> word.uppercase() }
                    }_${el.simpleName}")
                        .addParameter(el.simpleName.toString(), ClassName.bestGuess(el.asType().toString())).addCode("${
                            it.simpleName.toString().replaceFirstChar { word -> word.lowercase() }
                        }.${el.simpleName} = ${el.simpleName}").build())
                }
            }

            var constructorParameters = listOf<String>()
            it.enclosedElements.forEach { el ->
                if (el.simpleName.toString() == INIT_FUNC) {
                    val parameters = el.asType().toString().replace("(", "").replace(")void", "")
                    if (parameters.isNotEmpty())
                        constructorParameters = parameters.split(",")
                }
            }

            if (constructorParameters.isEmpty()) {
                objBuilder!!.addProperty(
                    PropertySpec.builder(
                        className.simpleName.replaceFirstChar { word -> word.lowercase() }, className
                    ).mutable(false).delegate("lazy { ${it.simpleName}() }").build()
                )
            } else {
                constructorParameters.forEach { param ->
                    val nameParam = param.removeRange(0, param.indexOfLast { char -> char == '.' } + 1)
                        .replaceFirstChar { word -> word.lowercase() }

                    objBuilder!!.addProperty(
                        PropertySpec.builder(nameParam, ClassName.bestGuess(param))
                            .addModifiers(KModifier.PRIVATE, KModifier.LATEINIT)
                            .mutable(true).build()
                    )

                    objBuilder!!.addFunction(FunSpec.builder("$SET_FUNC_PREFIX${
                        it.simpleName.toString().replaceFirstChar { word -> word.uppercase() }
                    }_$nameParam").addParameter(nameParam, ClassName.bestGuess(param))
                        .addCode("this.$nameParam = $nameParam").build())
                }

                objBuilder!!.addProperty(
                    PropertySpec.builder(
                        className.simpleName.replaceFirstChar { word -> word.lowercase() }, className
                    ).mutable(false).delegate(
                        "lazy { ${it.simpleName}(${
                            constructorParameters.map { param ->
                                param.removeRange(0, param.indexOfLast { char -> char == '.' } + 1)
                                    .replaceFirstChar { word -> word.lowercase() }
                            }.toString().replace("[", "").replace("]", "")
                        }) }"
                    ).build()
                )
            }
        }

        elementsWithDISetup?.forEach {
            val setupClassName = ClassName.bestGuess(it.asType().toString())
            val name = setupClassName.simpleName.replaceFirstChar { word -> word.lowercase() }
            objBuilder!!.addProperty(
                PropertySpec.builder(
                    setupClassName.simpleName.replaceFirstChar { word -> word.lowercase() }, setupClassName
                ).mutable(false).delegate("lazy { ${setupClassName.simpleName}() }").build()
            )
            var needCreate = false
            it.enclosedElements.forEach { el ->
                if (el.simpleName.toString() == INIT_FUNC) {
                    needCreate = true
                } else if (needCreate) {
                    val className = ClassName.bestGuess(el.asType().toString()
                        .removeRange(0, el.asType().toString().indexOfLast { char -> char == ')' } + 1))
                    objBuilder!!.addProperty(
                        PropertySpec.builder(
                            className.simpleName.replaceFirstChar { word -> word.lowercase() }, className
                        ).mutable(false).delegate("lazy { ${name}.${el.simpleName}() }").build()
                    )
                }
            }

            val annotation = it.getAnnotation(Setup::class.java)
            annotation.packages.forEach { pack ->
                if (funBuilder == null) funBuilder = FunSpec.builder("init")

                val typeElement = processingEnv.elementUtils.getTypeElement("$pack.$FILE_NAME")
                processingEnv.elementUtils.getAllMembers(typeElement).forEach { member ->
                    if (member.simpleName.contains(SET_FUNC_PREFIX) && member.kind == ElementKind.METHOD) {
                        funBuilder!!.addCode("$pack.$FILE_NAME.${member.simpleName}(${
                            member.simpleName.toString()
                                .removeRange(
                                    0,
                                    member.simpleName.toString().indexOfLast { char -> char == '_' } + 1)
                        })\n")
                    }
                }
            }
        }

        elementsWithDISingletonRoom?.forEach {
            var needCreate = false
            it.enclosedElements.forEach { el ->
                if (el.simpleName.toString() == INIT_FUNC) {
                    needCreate = true
                } else if (needCreate) {
                    val className = ClassName.bestGuess(el.asType().toString().removeRange(0, 2))
                    objBuilder!!.addProperty(
                        PropertySpec.builder(
                            className.simpleName.replaceFirstChar { word -> word.lowercase() }, className
                        ).mutable(false).delegate(
                            "lazy { ${
                                it.simpleName.toString().replaceFirstChar { word -> word.lowercase() }
                            }.${el.simpleName}() }"
                        ).build()
                    )
                }
            }
        }

        if (funBuilder != null && roundEnv?.processingOver() == true) objBuilder!!.addFunction(funBuilder!!.build())

        if (rootPackage == PACKAGE_NAME) {
            roundEnv?.rootElements?.forEach {
                if (it.toString().contains(BUILD_CONFIG_PACKAGE)) {
                    rootPackage = it.toString().replace(BUILD_CONFIG_PACKAGE, "")
                }
            }
        }

        if (objBuilder != null && roundEnv?.processingOver() == true) {
            objBuilder!!.addProperty(
                PropertySpec.builder(ROOT_PACKAGE, stringClassName).addModifiers(KModifier.CONST)
                    .mutable(false).initializer("\"$rootPackage\"").build()
            )

            val file = FileSpec.builder(rootPackage, FILE_NAME).addType(objBuilder!!.build()).build()
            val generatedDirectory = processingEnv.options[KAPT_KOTLIN_GENERATED_OPTION_NAME]
            file.writeTo(File(generatedDirectory, "$FILE_NAME.kt"))
        }

        return true
    }
}