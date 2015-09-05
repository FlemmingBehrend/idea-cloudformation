package com.intellij.aws.cloudformation;

import com.google.common.base.Charsets;
import com.intellij.icons.AllIcons;
import com.intellij.json.JsonLanguage;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.ex.FileTypeIdentifiableByVirtualFile;
import com.intellij.openapi.util.io.ByteSequence;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.FileSystemInterface;
import com.intellij.openapi.vfs.newvfs.impl.StubVirtualFile;
import com.intellij.util.Processor;
import org.apache.commons.lang.ArrayUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;

public class CloudFormationFileType extends LanguageFileType implements FileTypeIdentifiableByVirtualFile {
  public static final CloudFormationFileType INSTANCE = new CloudFormationFileType();

  private static final String EXTENSION = "template";
  private static final byte[] BYTES_TO_DETECT_CFN_FILE = CloudFormationSections.FormatVersion.getBytes(Charsets.US_ASCII);

  public CloudFormationFileType() {
    super(JsonLanguage.INSTANCE);
  }

  @NotNull
  @Override
  public String getName() {
    return "AWSCloudFormation";
  }

  @NotNull
  @Override
  public String getDescription() {
    return "AWS CloudFormation templates";
  }

  @NotNull
  @Override
  public String getDefaultExtension() {
    return "";
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return AllIcons.FileTypes.Json;
  }

  @Override
  public boolean isMyFileType(@NotNull VirtualFile file) {
    return CloudFormationFileType.EXTENSION.equalsIgnoreCase(file.getExtension()) && detectFromContent(file);
  }

  private boolean detectFromContent(@NotNull VirtualFile file) {
    if (file instanceof StubVirtualFile) {
      // Helps New -> File get correct file type
      return true;
    }

    VirtualFileSystem virtualFileSystem;
    try {
      virtualFileSystem = file.getFileSystem();
    } catch (UnsupportedOperationException ignored) {
      return false;
    }

    if (!(virtualFileSystem instanceof FileSystemInterface)) {
      return false;
    }

    try {
      final FileSystemInterface fileSystem = (FileSystemInterface) virtualFileSystem;
      final InputStream inputStream = fileSystem.getInputStream(file);
      try {
        return FileUtil.processFirstBytes(inputStream, 1024, new Processor<ByteSequence>() {
          @Override
          public boolean process(ByteSequence byteSequence) {
            return findArray(byteSequence.getBytes(), BYTES_TO_DETECT_CFN_FILE) >= 0;
          }
        });
      } finally {
        inputStream.close();
      }
    } catch (IOException ignored) {
      return false;
    }
  }

  private static int findArray(byte[] array, byte[] subArray) {
    return Collections.indexOfSubList(Arrays.asList(ArrayUtils.toObject(array)), Arrays.asList(ArrayUtils.toObject(subArray)));
  }
}
