package com.github.zhurlik.descriptor

import com.github.zhurlik.extension.JBossModule
import groovy.util.logging.Slf4j
import groovy.util.slurpersupport.GPathResult

import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory

import static javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI

/**
 * Core class to generate and to check xml descriptors for JBoss Modules
 *
 * @author zhurlik@gmail.com
 */
@Slf4j
abstract class Builder<T extends JBossModule> extends Xsd {

    static final factory = SchemaFactory.newInstance(W3C_XML_SCHEMA_NS_URI)

    abstract String getXmlDescriptor(final T module)

    abstract StreamSource getXsd()

    /**
     * Returns a path where will be stored JBoss Module under server.
     *
     * @return string like org/apache/log4j
     */
    abstract String getPath(JBossModule module)

    JBossModule makeModule(final String txt) {
        //result
        JBossModule jbModule = new JBossModule('empty')
        jbModule.ver = getVersion()

        def xml = new XmlSlurper().parseText(txt)

        if ('configuration' == xml.name()) {
            parseConfigurationTag(xml, jbModule)

            log.debug '>> Module: \'{}\' has been created', jbModule.name
            return jbModule
        }

        if ('module-alias' == xml.name()) {
            parseModuleAliasTag(jbModule, xml)

            log.debug '>> Module: \'{}\' has been created', jbModule.name
            return jbModule
        }

        xml.attributes().each() {
            switch (it.key) {
                case 'slot': jbModule.slot = it.value
                    break
                case 'name': jbModule.moduleName = it.value
                    jbModule.name = it.value
                    break
            }
        }

        jbModule.mainClass = xml.'main-class'.@name

        xml.properties.each() {
            it.property.each() { p ->
                jbModule.properties.put(p.@name.toString(), p.@value.toString())
            }
        }

        xml.resources.each() {
            it.'resource-root'.each() { r ->

                def complexEl = [:]

                if (r.attributes().size() == 1) {
                    complexEl.path = r.@path.text()
                } else {
                    complexEl.name = r.@name.text()
                    complexEl.path = r.@path.text()
                }

                r.filter.each() { f ->
                    def filter = [:]
                    f.include.each() {
                        filter.include = f.include.@path.text()
                    }
                    f.exclude.each() {
                        filter.exclude = f.exclude.@path.text()
                    }
                    if (f.'exclude-set'.children().size() > 0) {
                        filter.exclude = f.'exclude-set'.path.collect() { it.@name.text() }
                    }
                    if (f.'include-set'.children().size() > 0) {
                        filter.include = f.'include-set'.path.collect() { it.@name.text() }
                    }
                    complexEl.filter = filter
                }

                jbModule.resources.add(complexEl)
            }

            it.artifact.each {
                def el = [:]
                el.type = 'artifact'
                it.attributes().each {
                    el.put(it.key, it.value)
                }
                jbModule.resources.add(el)
            }

            it.'native-artifact'.each {
                def el = [:]
                el.type = 'native-artifact'
                it.attributes().each {
                    el.put(it.key, it.value)
                }
                jbModule.resources.add(el)
            }
        }

        // exports
        xml.exports.each() {
            def map = [:]
            it.include.each() {
                map.include = it.@path.text()
            }
            it.exclude.each() {
                map.exclude = it.@path.text()
            }
            if (it.'exclude-set'.children().size() > 0) {
                map.exclude = it.'exclude-set'.path.collect() { it.@name.text() }
            }
            if (it.'include-set'.children().size() > 0) {
                map.include = it.'include-set'.path.collect() { it.@name.text() }
            }
            jbModule.exports = map
        }

        // permissions
        xml.permissions.each { p ->
            p.grant.each {
                def g = [:]
                it.attributes().each({ g.putAt(it.key, it.value) })
                jbModule.permissions.add(g)
            }
        }

        xml.dependencies.each() {
            // modules
            parseModules(it, jbModule)

            // systems
            parseSystems(it, jbModule)
        }

        log.debug '>> Module: \'{}\' has been created', jbModule.name
        return jbModule
    }

    /**
     * To parse <xsd:complexType name="systemDependencyType">
     *
     * @param it a little bit of xml
     * @param jbModule
     * @return
     */
    def parseSystems(def it, final JBossModule jbModule) {
        it.system.each() { s ->
            def system = [:]

            if (s.@export.toBoolean() == true) {
                system.export = true
            }

            // paths
            def paths = []
            system.type = 'system'

            s.paths.each {
                paths += it.path.collect({ it.@name.text() })
            }

            if (!paths.empty) {
                system.paths = paths
            }

            // exports
            s.exports.each() {
                def map = [:]
                it.include.each() {
                    map.include = it.@path.text()
                }
                it.exclude.each() {
                    map.exclude = it.@path.text()
                }
                if (it.'exclude-set'.children().size() > 0) {
                    map.exclude = it.'exclude-set'.path.collect() { it.@name.text() }
                }
                if (it.'include-set'.children().size() > 0) {
                    map.include = it.'include-set'.path.collect() { it.@name.text() }
                }
                system.exports = map
            }

            jbModule.dependencies.add(system)
        }
    }

    /**
     * To parse <xsd:complexType name="moduleDependencyType">
     *
     * @param it a little bit of xml
     * @param jbModule
     * @return
     */
    def parseModules(def it, final JBossModule jbModule) {
        it.module.each() { d ->
            def dep = [:]
            if (d.attributes().size() == 1) {
                dep.name = d.@name.toString()
            } else {
                d.attributes().each() {
                    dep[it.key] = it.value
                }
            }

            // imports
            d.imports.each() {
                def map = [:]
                it.include.each() {
                    map.include = it.@path.text()
                }
                it.exclude.each() {
                    map.exclude = it.@path.text()
                }
                if (it.'exclude-set'.children().size() > 0) {
                    map.exclude = it.'exclude-set'.path.collect() { it.@name.text() }
                }
                if (it.'include-set'.children().size() > 0) {
                    map.include = it.'include-set'.path.collect() { it.@name.text() }
                }
                dep.imports = map
            }

            // exports
            d.exports.each() {
                def map = [:]
                it.include.each() {
                    map.include = it.@path.text()
                }
                it.exclude.each() {
                    map.exclude = it.@path.text()
                }
                if (it.'exclude-set'.children().size() > 0) {
                    map.exclude = it.'exclude-set'.path.collect() { it.@name.text() }
                }
                if (it.'include-set'.children().size() > 0) {
                    map.include = it.'include-set'.path.collect() { it.@name.text() }
                }
                dep.exports = map
            }

            jbModule.dependencies.add(dep)
        }
    }

    /**
     * To parse a root element for a module alias declaration.
     *
     * @param jbModule
     * @param xml
     */
    private void parseModuleAliasTag(JBossModule jbModule, GPathResult xml) {
        jbModule.moduleAlias = true
        xml.attributes().each() {
            switch (it.key) {
                case 'slot': jbModule.slot = it.value
                    break
                case 'name': jbModule.moduleName = it.value
                    jbModule.name = it.value
                    break
                case 'target-name': jbModule.targetName = it.value
                    break
            }
        }
    }

    /**
     * To parse a root element <configuration>.
     *
     * @param xml
     * @param jbModule
     */
    private void parseConfigurationTag(GPathResult xml, JBossModule jbModule) {

        jbModule.moduleConfiguration = true
        jbModule.defaultLoader = xml.@'default-loader'.text()

        xml.loader.each { l ->
            def el = [:]
            el.name = l.@name.text()

            l.import.each {
                el.import = it.text()
            }

            l.'module-path'.each {
                el['module-path'] = it.@name.text()
            }

            jbModule.loaders.add(el)
        }

        if (jbModule.loaders.empty) {
            jbModule.loaders.add(xml.@'default-loader'.text())
        }
    }

    /**
     * To validate a xml descriptors.
     *
     * @param xml xml descriptor
     * @return true if valid
     */
    boolean isValid(final String xml) {
        try {
            def schema = factory.newSchema(getXsd())
            def validator = schema.newValidator()
            validator.validate(new StreamSource(new StringReader(xml)))
            return true
        } catch (all) {
            log.error '>> ERROR: ' + all
            return false
        }
    }
}


