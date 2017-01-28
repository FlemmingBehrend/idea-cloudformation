package com.intellij.aws.cloudformation.tests

import com.intellij.aws.cloudformation.CloudFormationProblem
import com.intellij.aws.cloudformation.model.CfnNode
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.psi.PsiFile
import com.intellij.rt.execution.junit.FileComparisonFailure
import junit.framework.TestCase
import java.io.File
import java.io.IOException
import java.io.StringWriter

object TestUtil {
  fun getTestDataPath(relativePath: String): String {
    return getTestDataFile(relativePath).path + File.separator
  }

  fun getTestDataFile(relativePath: String): File {
    return File(testDataRoot, relativePath)
  }

  private val testDataRoot: File
    get() = File("testData").absoluteFile

  fun getTestDataPathRelativeToIdeaHome(relativePath: String): String {
    val homePath = File(PathManager.getHomePath())
    val testDir = File(testDataRoot, relativePath)

    val relativePathToIdeaHome = FileUtil.getRelativePath(homePath, testDir) ?:
        throw RuntimeException("getTestDataPathRelativeToIdeaHome: FileUtil.getRelativePath('$homePath', '$testDir') returned null")

    return relativePathToIdeaHome
  }

  fun nodeToString(node: CfnNode): String = MyToStringStyle.toString(node, arrayOf("allTopLevelProperties", "functionId"))

  fun checkContent(expectFile: File, actualContent: String) {
    val actualNormalized = StringUtil.trimEnd(StringUtil.convertLineSeparators(actualContent), '\n') + '\n'

    if (!expectFile.exists()) {
      expectFile.writeText(actualNormalized)
      TestCase.fail("Wrote ${expectFile.path} with actual content")
    }

    val expectText: String
    try {
      expectText = StringUtil.convertLineSeparators(FileUtil.loadFile(expectFile, CharsetToolkit.UTF8_CHARSET))
    } catch (e: IOException) {
      throw RuntimeException(e)
    }

    if (expectText != actualNormalized) {
      throw FileComparisonFailure("Expected text mismatch", expectText, actualNormalized, expectFile.path)
    }
  }

  private fun addMarkers(s: String, markers: List<Pair<Int, String>>): String {
    return markers
        .sortedBy { it.first }
        .fold(
            Pair(s, 0),
            { acc, el ->
              val (str, drift) = acc
              val (pos, marker) = el

              Pair(str.substring(0, pos + drift) + marker + str.substring(pos + drift), drift + marker.length)
            }
        ).first
  }

  fun renderProblems(file: PsiFile, problems: List<CloudFormationProblem>): String {
    if (problems.isEmpty()) {
      return "No problems"
    }

    val writer = StringWriter()

    val markers = problems.mapIndexed { n, problem ->
      val el = problem.element
      listOf(Pair(el.textOffset, "$n@<"), Pair(el.textOffset + el.textLength, ">"))
    }.flatten()

    val textWithMarkers = addMarkers(file.text, markers)

    writer.append(textWithMarkers)
    if (!textWithMarkers.endsWith("\n")) {
      writer.appendln()
    }

    if (problems.isNotEmpty()) {
      writer.appendln()
    }

    for (problem in problems.withIndex()) {
      writer.appendln("${problem.index}: ${problem.value.description}")
    }

    return writer.toString()
  }
}
