package com.github.zhurlik.extension
import com.github.zhurlik.Ver
import com.github.zhurlik.descriptor.Builder
import groovy.util.logging.Slf4j
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.After
import org.junit.Before
import org.junit.Test

import static java.io.File.separator
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertNull
import static org.junit.Assert.assertTrue
/**
 * A common stuff for testing.
 *
 * @author zhurlik@gmail.com
 */
@Slf4j
abstract class BasicJBossModuleTest {
    protected JBossModule module
    protected Builder<JBossModule> builder = getVersion().builder
    protected
    final File projectDir = new File(getClass().getClassLoader().getResource('').toURI().path + separator + 'projectTest')
    protected String prefix = ""

    @Before
    public void setUp() throws Exception {
        if (!projectDir.exists()) {
            assert projectDir.mkdir()
        }
    }

    @After
    public void tearDown() throws Exception {
        if (projectDir.exists() && projectDir.isDirectory()) {
            assert projectDir.deleteDir()
        }
    }

    @Test
    public void testValidation() throws Exception {
        try {
            module  = new JBossModule("wrong")
            module.slot = '~#?!'
            assertFalse true
        } catch (AssertionError error) {
            assertTrue true
        }

        try {
            module  = new JBossModule("wrong")
            module.moduleName = '~#?!'
            assertFalse true
        } catch (AssertionError error) {
            assertTrue true
        }

        try {
            module  = new JBossModule("wrong")
            module.defaultLoader = '~#?!'
            assertFalse true
        } catch (AssertionError error) {
            assertTrue true
        }

        try {
            module  = new JBossModule("wrong")
            module.targetName = '~#?!'
            assertFalse true
        } catch (AssertionError error) {
            assertTrue true
        }
    }

    @Test
    public void testPermissionsTag() throws Exception {
        // 1
        module = new JBossModule('testModule')
        module.ver = getVersion()
        module.moduleName = 'my.module'
        module.slot = '1.0'
        module.permissions = [
                'permission0',
                [permission: 'permission1'],
                [permission: 'permission2', name: 'test-name', actions: 'test-actions']
        ]

        String xml = "<?xml version='1.0' encoding='utf-8'?>\n" +
                "<module xmlns='urn:jboss:module:" + getVersion().number + "' name='my.module' slot='1.0'>\n" +
                "  <permissions>\n" +
                "    <grant permission='permission0' />\n" +
                "    <grant permission='permission1' />\n" +
                "    <grant actions='test-actions' name='test-name' permission='permission2' />\n" +
                "  </permissions>\n" +
                "</module>"
        assertEquals 'Case1:', xml, module.moduleDescriptor
        assert module.valid
        assertEquals 'Reverse:', xml, builder.makeModule(xml).moduleDescriptor
    }


    @Test
    public void testConfigurationTag() throws Exception {
        // 1
        try {
            module = new JBossModule('testModule')
            module.ver = getVersion()
            module.moduleName = 'my.module'
            module.moduleConfiguration = true
            assert false
        } catch (AssertionError ex) {
            assertTrue 'Case1:', true
        }

        //2
        module = new JBossModule('testModule')
        module.ver = getVersion()
        module.moduleName = 'my.module'
        module.moduleConfiguration = true
        module.defaultLoader = 'test-default1'
        String xml = "<?xml version='1.0' encoding='utf-8'?>\n" +
                "<configuration xmlns='urn:jboss:module:" + getVersion().number + "' default-loader='test-default1'>\n" +
                "  <loader name='test-default1' />\n" +
                "</configuration>"
        assertEquals 'Case2:', xml, module.moduleDescriptor
        assert module.valid
        assertEquals 'Reverse:', xml, builder.makeModule(xml).moduleDescriptor

        //3
        module = new JBossModule('testModule')
        module.ver = getVersion()
        module.moduleName = 'my.module'
        module.moduleConfiguration = true
        module.defaultLoader = '_test-default1'
        module.loaders = ['_test-default1', [name: 'loader2']]
        xml = "<?xml version='1.0' encoding='utf-8'?>\n" +
                "<configuration xmlns='urn:jboss:module:" + getVersion().number + "' default-loader='_test-default1'>\n" +
                "  <loader name='_test-default1' />\n" +
                "  <loader name='loader2' />\n" +
                "</configuration>"
        assertEquals 'Case3:', xml, module.moduleDescriptor
        assert module.valid
        assertEquals 'Reverse:', xml, builder.makeModule(xml).moduleDescriptor

        //4
        module = new JBossModule('testModule')
        module.ver = getVersion()
        module.moduleName = 'my.module'
        module.moduleConfiguration = true
        module.defaultLoader = '_test-default1'
        module.loaders = ['_test-default1', [name: 'loader2'], [name: 'loader3', import: 'test-import'], [name: 'loader4', 'module-path': 'test-path']]
        xml = "<?xml version='1.0' encoding='utf-8'?>\n" +
                "<configuration xmlns='urn:jboss:module:" + getVersion().number + "' default-loader='_test-default1'>\n" +
                "  <loader name='_test-default1' />\n" +
                "  <loader name='loader2' />\n" +
                "  <loader name='loader3'>\n" +
                "    <import>test-import</import>\n" +
                "  </loader>\n" +
                "  <loader name='loader4'>\n" +
                "    <module-path name='test-path' />\n" +
                "  </loader>\n" +
                "</configuration>"
        assertEquals 'Case4:', xml, module.moduleDescriptor
        assert module.valid
        assertEquals 'Reverse:', xml, builder.makeModule(xml).moduleDescriptor
    }

    @Test
    /**
     * Test for downloading a resource and saving a jboss module
     */
    public void testMake() throws Exception {
        log.debug '>> Test for making a module and saving locally...'
        // 1
        module = new JBossModule('log4j')
        module.ver = getVersion()
        // only for testServer
        module.servers = ['testServer']
        module.moduleName = 'org.apache.log4j'
        module.resources = ['log4j-1.2.17.jar']

        // empty project with all needed
        final Project project = ProjectBuilder.builder()
                .withName('test-project')
                .withProjectDir(projectDir)
                .build()
        project.apply plugin: 'com.github.zhurlik.jbossmodules'

        // using a maven to download jar files
        project.repositories {
            mavenCentral()
        }

        // to have a reference for jar file
        project.dependencies {
            jbossmodules 'log4j:log4j:1.2.17'

        }

        // describe an instance of jboss server
        project.jbossrepos {
            testServer {
                home = projectDir.path + separator + "testServer"
                version = owner.getVersion()
            }
            testServer1 {
                home = projectDir.path + separator + "testServer1"
            }
        }

        // test call
        module.makeLocally(project)

        // nothing for testServer1
        assertNull getClass().getClassLoader().getResource('projectTest/build/install/testServer1/modules/' + prefix + 'org/apache/log4j/main/log4j-1.2.17.jar')
        assertNull getClass().getClassLoader().getResource('projectTest/build/install/testServer1/modules/' + prefix + 'org/apache/log4j/main/module.xml')

        assertTrue new File(getClass().getClassLoader().getResource('projectTest/build/install/testServer/modules/' + prefix + 'org/apache/log4j/main/log4j-1.2.17.jar').toURI().path).exists()
        assertTrue new File(getClass().getClassLoader().getResource('projectTest/build/install/testServer/modules/' + prefix + 'org/apache/log4j/main/module.xml').toURI().path).exists()
        assertEquals 'Module Descriptor:', "<?xml version='1.0' encoding='utf-8'?>\n" +
                "<module xmlns='urn:jboss:module:" + getVersion().number + "' name='org.apache.log4j'>\n" +
                "  <resources>\n" +
                "    <resource-root path='log4j-1.2.17.jar' />\n" +
                "  </resources>\n" +
                "</module>", new File(getClass().getClassLoader().getResource('projectTest/build/install/testServer/modules/' + prefix + 'org/apache/log4j/main/module.xml').toURI().path).text
    }

    @Test
    public void testModuleAliasTag() throws Exception {
        // 1
        module = new JBossModule('testModule')
        module.ver = getVersion()
        module.moduleName = 'my.module'
        module.slot = '1.0'
        module.targetName = 'testTarget'
        module.setModuleAlias(true)
        String xml = "<?xml version='1.0' encoding='utf-8'?>\n" +
                "<module-alias xmlns='urn:jboss:module:" + getVersion().number + "' name='my.module' slot='1.0' target-name='testTarget' />"
        assertEquals 'Case1:', xml, module.moduleDescriptor
        assert module.valid
        assertEquals 'Reverse:', xml, builder.makeModule(xml).moduleDescriptor
    }

    @Test
    public void testDependencies() throws Exception {
        //1
        module = new JBossModule('testModule')
        module.ver = getVersion()
        module.moduleName = 'my.module'
        module.dependencies = [[type: 'system']]
        String xml = "<?xml version='1.0' encoding='utf-8'?>\n" +
                "<module xmlns='urn:jboss:module:" + getVersion().number + "' name='my.module'>\n" +
                "  <dependencies>\n" +
                "    <system />\n" +
                "  </dependencies>\n" +
                "</module>"
        assertEquals 'Case1:', xml, module.moduleDescriptor
        assert !module.valid

        // 2
        module = new JBossModule('testModule')
        module.ver = getVersion()
        module.moduleName = 'my.module'
        module.dependencies = [
                'module1', 'module2',
                [name   : 'module3', slot: '1.3', services: 'none', optional: true, export: 'false',
                 imports: [exclude: ['exclude1', 'exclude2']],
                 exports: [include: '**']
                ],
                [type: 'system', paths: 'test-path'],
                [type: 'system', export: true, paths: ['path1', 'path2'], exports: [exclude: ['exclude1', 'exclude2']]],
                [type: 'system', export: false, paths: 'test-path', exports: [include: '**']]
        ]
        module.slot = '1.0'
        xml = "<?xml version='1.0' encoding='utf-8'?>\n" +
                "<module xmlns='urn:jboss:module:" + getVersion().number + "' name='my.module' slot='1.0'>\n" +
                "  <dependencies>\n" +
                "    <module name='module1' />\n" +
                "    <module name='module2' />\n" +
                "    <module export='false' name='module3' optional='true' services='none' slot='1.3'>\n" +
                "      <imports>\n" +
                "        <exclude-set>\n" +
                "          <path name='exclude1' />\n" +
                "          <path name='exclude2' />\n" +
                "        </exclude-set>\n" +
                "      </imports>\n" +
                "      <exports>\n" +
                "        <include path='**' />\n" +
                "      </exports>\n" +
                "    </module>\n" +
                "    <system>\n" +
                "      <paths>\n" +
                "        <path name='test-path' />\n" +
                "      </paths>\n" +
                "    </system>\n" +
                "    <system export='true'>\n" +
                "      <paths>\n" +
                "        <path name='path1' />\n" +
                "        <path name='path2' />\n" +
                "      </paths>\n" +
                "      <exports>\n" +
                "        <exclude-set>\n" +
                "          <path name='exclude1' />\n" +
                "          <path name='exclude2' />\n" +
                "        </exclude-set>\n" +
                "      </exports>\n" +
                "    </system>\n" +
                "    <system>\n" +
                "      <paths>\n" +
                "        <path name='test-path' />\n" +
                "      </paths>\n" +
                "      <exports>\n" +
                "        <include path='**' />\n" +
                "      </exports>\n" +
                "    </system>\n" +
                "  </dependencies>\n" +
                "</module>"
        assertEquals 'Case2:', xml, module.moduleDescriptor
        assert module.valid
        assertEquals 'Reverse:', xml, builder.makeModule(xml).moduleDescriptor
    }

    abstract protected Ver getVersion()
}
