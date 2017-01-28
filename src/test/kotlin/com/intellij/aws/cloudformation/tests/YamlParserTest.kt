package com.intellij.aws.cloudformation.tests

import com.intellij.aws.cloudformation.CloudFormationParser
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import org.apache.commons.lang.SystemUtils
import java.io.File

class YamlParserTest : LightPlatformCodeInsightTestCase() {
  fun testFunShortForm() = runTest("funShortForm")
  fun testQuotedTextValue() = runTest("quotedTextValue")

  fun runTest(name: String) {
    configureByFile("$name.yaml")
    val parsed = CloudFormationParser.parse(myFile)
    TestUtil.checkContent(
        File(testDataPath, "$name.expected"),
        TestUtil.renderProblems(myFile, parsed.problems) + SystemUtils.LINE_SEPARATOR + TestUtil.nodeToString(parsed.root)
    )
  }

  override fun getTestDataPath(): String {
    return TestUtil.getTestDataPath("parser/yaml")
  }
}
