package ee.carlrobert.codegpt.autoimport

import com.intellij.openapi.util.TextRange
import org.assertj.core.api.Assertions.assertThat
import testsupport.IntegrationTest

class AutoImportOrchestratorTest : IntegrationTest() {

    fun testPreviewImportsForJavaFile() {
        myFixture.addFileToProject(
            "com/test/util/ListClass.java",
            "package com.test.util; public class ListClass<E> {}"
        )
        myFixture.addFileToProject(
            "com/test/lang/StringHelper.java",
            "package com.test.lang; public class StringHelper {}"
        )
        myFixture.addFileToProject(
            "com/test/data/CustomClass.java",
            "package com.test.data; public class CustomClass {}"
        )
        val file = myFixture.configureByText(
            "Test.java",
            """
            package com.test;

            public class Test {
                public void test() {
                    StringHelper text = new StringHelper();
                    CustomClass instance = new CustomClass();
                    ListClass<String> list = new ListClass<>();
                }
            }
            """.trimIndent()
        )
        val original = file.text

        val result = AutoImportOrchestrator.previewImports(myFixture.editor)

        assertThat(file.text).isEqualTo(original)
        assertThat(result).isEqualTo(
            """
            package com.test;

            import com.test.data.CustomClass;
            import com.test.lang.StringHelper;
            import com.test.util.ListClass;

            public class Test {
                public void test() {
                    StringHelper text = new StringHelper();
                    CustomClass instance = new CustomClass();
                    ListClass<String> list = new ListClass<>();
                }
            }
            """.trimIndent()
        )
    }

    fun testPreviewImportsForKotlinFile() {
        myFixture.addFileToProject(
            "com/test/util/ListClass.kt",
            "package com.test.util; class ListClass<E>"
        )
        myFixture.addFileToProject(
            "com/test/lang/PrintHelper.kt",
            "package com.test.lang; object PrintHelper { fun println(msg: String) {} }"
        )
        myFixture.addFileToProject(
            "com/test/data/CustomClass.kt",
            "package com.test.data; class CustomClass"
        )
        val file = myFixture.configureByText(
            "Test.kt",
            """
            class Test {
                fun test() {
                    PrintHelper.println("hello")
                    val instance = CustomClass()
                    val list = ListClass<String>()
                }
            }
            """.trimIndent()
        )
        val original = file.text

        val result = AutoImportOrchestrator.previewImports(myFixture.editor)

        assertThat(file.text).isEqualTo(original)
        assertThat(result).isEqualTo(
            """
            import com.test.lang.PrintHelper
            import com.test.data.CustomClass
            import com.test.util.ListClass

            class Test {
                fun test() {
                    PrintHelper.println("hello")
                    val instance = CustomClass()
                    val list = ListClass<String>()
                }
            }
            """.trimIndent()
        )
    }

    fun testPreviewImportsWithRangeForJava() {
        myFixture.addFileToProject(
            "com/a/Aaa.java",
            "package com.a; public class Aaa {}"
        )
        myFixture.addFileToProject(
            "com/b/Bbb.java",
            "package com.b; public class Bbb {}"
        )
        myFixture.addFileToProject(
            "com/c/Ccc.java",
            "package com.c; public class Ccc {}"
        )

        val file = myFixture.configureByText(
            "Client.java",
            """
            package client;

            public class Client {
                public void test() {
                    Aaa a = new Aaa();
                    Bbb b = new Bbb();
                    Ccc c = new Ccc();
                }
            }
            """.trimIndent()
        )
        val original = file.text

        val start = original.indexOf("Bbb")
        val end = start + "Bbb".length
        val result = AutoImportOrchestrator.previewImports(myFixture.editor, TextRange(start, end))

        assertThat(file.text).isEqualTo(original)
        assertThat(result).isEqualTo(
            """
            package client;

            import com.b.Bbb;

            public class Client {
                public void test() {
                    Aaa a = new Aaa();
                    Bbb b = new Bbb();
                    Ccc c = new Ccc();
                }
            }
            """.trimIndent()
        )
    }

    fun testPreviewImportsWithRangeForKotlin() {
        myFixture.addFileToProject(
            "com/a/Aaa.kt",
            "package com.a; class Aaa"
        )
        myFixture.addFileToProject(
            "com/b/Bbb.kt",
            "package com.b; class Bbb"
        )
        myFixture.addFileToProject(
            "com/c/Ccc.kt",
            "package com.c; class Ccc"
        )

        val file = myFixture.configureByText(
            "Client.kt",
            """
            class Client {
                fun test() {
                    val a = Aaa()
                    val b = Bbb()
                    val c = Ccc()
                }
            }
            """.trimIndent()
        )
        val original = file.text

        val start = original.indexOf("Bbb()")
        val end = start + "Bbb()".length
        val result = AutoImportOrchestrator.previewImports(myFixture.editor, TextRange(start, end))

        assertThat(file.text).isEqualTo(original)
        assertThat(result).isEqualTo(
            """
            import com.b.Bbb

            class Client {
                fun test() {
                    val a = Aaa()
                    val b = Bbb()
                    val c = Ccc()
                }
            }
            """.trimIndent()
        )
    }

    fun testReturnsSameContentWhenNoUnresolvedJava() {
        val file = myFixture.configureByText(
            "NoUnresolved.java",
            """
            package client;

            public class NoUnresolved {
                public void test() {
                    int x = 1;
                    java.util.List<String> list = new java.util.ArrayList<>();
                }
            }
            """.trimIndent()
        )
        val original = file.text

        val result = AutoImportOrchestrator.previewImports(myFixture.editor)

        assertThat(result).isEqualTo(original)
    }

    fun testReturnsSameContentWhenNoUnresolvedKotlin() {
        val file = myFixture.configureByText(
            "NoUnresolved.kt",
            """
            class NoUnresolved {
                fun test() {
                    val x = 1
                }
            }
            """.trimIndent()
        )
        val original = file.text

        val result = AutoImportOrchestrator.previewImports(myFixture.editor)

        assertThat(result).isEqualTo(original)
    }

    fun testReturnsNullForUnsupportedFileType() {
        val file = myFixture.configureByText(
            "note.txt",
            "Just some content with Foo and Bar"
        )
        val original = file.text

        val result = AutoImportOrchestrator.previewImports(myFixture.editor)

        assertThat(file.text).isEqualTo(original)
        assertThat(result).isNull()
    }

    fun testJavaAvoidsImportForSamePackage() {
        myFixture.addFileToProject(
            "com/test/util/SamePkg.java",
            "package com.test.util; public class SamePkg {}"
        )
        myFixture.addFileToProject(
            "com/other/OtherOne.java",
            "package com.other; public class OtherOne {}"
        )
        val file = myFixture.configureByText(
            "Test.java",
            """
            package com.test.util;

            public class Test {
                public void test() {
                    SamePkg a = new SamePkg();
                    OtherOne o = new OtherOne();
                }
            }
            """.trimIndent()
        )
        val original = file.text
        val result = AutoImportOrchestrator.previewImports(myFixture.editor)

        assertThat(file.text).isEqualTo(original)
        assertThat(result).isEqualTo(
            """
            package com.test.util;

            import com.other.OtherOne;

            public class Test {
                public void test() {
                    SamePkg a = new SamePkg();
                    OtherOne o = new OtherOne();
                }
            }
            """.trimIndent()
        )
    }

    fun testDoesNotDuplicateExistingImportsJava() {
        myFixture.addFileToProject(
            "com/test/lang/StringHelper.java",
            "package com.test.lang; public class StringHelper {}"
        )
        myFixture.addFileToProject(
            "com/test/data/CustomClass.java",
            "package com.test.data; public class CustomClass {}"
        )
        val file = myFixture.configureByText(
            "Test.java",
            """
            package com.test;

            import com.test.lang.StringHelper;

            public class Test {
                public void test() {
                    StringHelper text = new StringHelper();
                    CustomClass instance = new CustomClass();
                }
            }
            """.trimIndent()
        )
        val original = file.text

        val result = AutoImportOrchestrator.previewImports(myFixture.editor)

        assertThat(file.text).isEqualTo(original)
        assertThat(result).isEqualTo(
            """
            package com.test;

            import com.test.data.CustomClass;
            import com.test.lang.StringHelper;

            public class Test {
                public void test() {
                    StringHelper text = new StringHelper();
                    CustomClass instance = new CustomClass();
                }
            }
            """.trimIndent()
        )
    }

    fun testDoesNotDuplicateExistingImportsKotlin() {
        myFixture.addFileToProject(
            "com/test/lang/PrintHelper.kt",
            "package com.test.lang; object PrintHelper { fun println(msg: String) {} }"
        )
        myFixture.addFileToProject(
            "com/test/data/CustomClass.kt",
            "package com.test.data; class CustomClass"
        )
        val file = myFixture.configureByText(
            "Test.kt",
            """
            import com.test.lang.PrintHelper

            class Test {
                fun test() {
                    PrintHelper.println("hello")
                    val instance = CustomClass()
                }
            }
            """.trimIndent()
        )
        val original = file.text

        val result = AutoImportOrchestrator.previewImports(myFixture.editor)

        assertThat(file.text).isEqualTo(original)
        assertThat(result).isEqualTo(
            """
            import com.test.lang.PrintHelper
            import com.test.data.CustomClass

            class Test {
                fun test() {
                    PrintHelper.println("hello")
                    val instance = CustomClass()
                }
            }
            """.trimIndent()
        )
    }

    fun testJavaAmbiguousSimpleNameChoosesAlphabeticalFirst() {
        myFixture.addFileToProject(
            "com/a/Dupe.java",
            "package com.a; public class Dupe {}"
        )
        myFixture.addFileToProject(
            "com/b/Dupe.java",
            "package com.b; public class Dupe {}"
        )
        val file = myFixture.configureByText(
            "Test.java",
            """
            package com.test;

            public class Test {
                public void test() {
                    Dupe d = null;
                }
            }
            """.trimIndent()
        )
        val original = file.text

        val result = AutoImportOrchestrator.previewImports(myFixture.editor)

        assertThat(file.text).isEqualTo(original)
        assertThat(result).isEqualTo(
            """
            package com.test;

            import com.a.Dupe;

            public class Test {
                public void test() {
                    Dupe d = null;
                }
            }
            """.trimIndent()
        )
    }

    fun testKotlinAmbiguousSimpleNameChoosesAlphabeticalFirst() {
        myFixture.addFileToProject(
            "com/a/Dupe.kt",
            "package com.a; class Dupe"
        )
        myFixture.addFileToProject(
            "com/b/Dupe.kt",
            "package com.b; class Dupe"
        )
        val file = myFixture.configureByText(
            "Test.kt",
            """
            class Test {
                fun test() {
                    val d = Dupe()
                }
            }
            """.trimIndent()
        )
        val original = file.text

        val result = AutoImportOrchestrator.previewImports(myFixture.editor)

        assertThat(file.text).isEqualTo(original)
        assertThat(result).isEqualTo(
            """
            import com.a.Dupe

            class Test {
                fun test() {
                    val d = Dupe()
                }
            }
            """.trimIndent()
        )
    }

    fun testMixedKnownAndUnknownSymbolsJava() {
        myFixture.addFileToProject(
            "com/a/Known.java",
            "package com.a; public class Known {}"
        )
        val file = myFixture.configureByText(
            "Test.java",
            """
            package com.test;

            public class Test {
                public void test() {
                    Known k = new Known();
                    UnknownFoo u = null;
                }
            }
            """.trimIndent()
        )
        val original = file.text

        val result = AutoImportOrchestrator.previewImports(myFixture.editor)

        assertThat(file.text).isEqualTo(original)
        assertThat(result).isEqualTo(
            """
            package com.test;

            import com.a.Known;

            public class Test {
                public void test() {
                    Known k = new Known();
                    UnknownFoo u = null;
                }
            }
            """.trimIndent()
        )
    }

    fun testRangeOutsideAnyReference() {
        myFixture.addFileToProject(
            "com/a/Aaa.java",
            "package com.a; public class Aaa {}"
        )
        val file = myFixture.configureByText(
            "Client.java",
            """
            package client;

            public class Client {
                public void test() {
                    Aaa a = new Aaa();
                }
            }
            """.trimIndent()
        )
        val original = file.text

        val result = AutoImportOrchestrator.previewImports(myFixture.editor, TextRange(0, 1))

        assertThat(result).isEqualTo(original)
    }
}
