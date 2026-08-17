package app.alpha.smoke

import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException

/**
 * `DocumentBuilderFactory`, ведущая себя как разборщик АНДРОИДА, а не Xerces
 * настольной JVM, — среда полевого краша импорта RC-XML.
 *
 * Android наследует «бросающие» реализации базового JAXP-класса: у него
 * `setXIncludeAware` кидает `UnsupportedOperationException` («This parser does
 * not support specification "Unknown" version "0.0"»), а незнакомые фичи
 * Xerces (`disallow-doctype-decl` и пр.) — `ParserConfigurationException`.
 * Настольный Xerces всё это молча поддерживает, поэтому чистый JVM-тест краш
 * не видел. Этот класс воспроизводит поведение Android на JVM:
 *
 * - [setXIncludeAware]/[isXIncludeAware] НЕ переопределены — наследуют бросок
 *   базового класса, ровно как реализация Android;
 * - [setFeature]/[getFeature] отвергают любую фичу;
 * - [setAttribute]/[getAttribute] отвергают любой атрибут;
 * - сам разбор делегируется штатной реализации JDK: нам нужна андроидная
 *   ПОВЕРХНОСТЬ НАСТРОЕК, а не другой парсер.
 *
 * Подставляется системным свойством `javax.xml.parsers.DocumentBuilderFactory`
 * (см. RcXmlAndroidParserRegressionTest).
 */
class AndroidLikeDocumentBuilderFactory : DocumentBuilderFactory() {

    override fun newDocumentBuilder(): DocumentBuilder {
        // Делегат ищется штатным newInstance при ВРЕМЕННО снятом системном
        // свойстве: иначе оно снова разрешилось бы в этот же класс и
        // зациклилось. (`newDefaultInstance` JDK 9+ здесь недоступен: тесты
        // компилируются против android.jar, где его нет.)
        val previous = System.getProperty(FACTORY_PROPERTY)
        System.clearProperty(FACTORY_PROPERTY)
        try {
            val delegate = DocumentBuilderFactory.newInstance()
            delegate.isNamespaceAware = isNamespaceAware
            delegate.isValidating = isValidating
            return delegate.newDocumentBuilder()
        } finally {
            if (previous != null) System.setProperty(FACTORY_PROPERTY, previous)
        }
    }

    companion object {
        const val FACTORY_PROPERTY = "javax.xml.parsers.DocumentBuilderFactory"
    }

    override fun setAttribute(name: String, value: Any?): Unit =
        throw IllegalArgumentException("attribute is not supported: $name")

    override fun getAttribute(name: String): Any =
        throw IllegalArgumentException("attribute is not supported: $name")

    override fun setFeature(name: String, value: Boolean): Unit =
        throw ParserConfigurationException("feature is not supported: $name")

    override fun getFeature(name: String): Boolean =
        throw ParserConfigurationException("feature is not supported: $name")
}
