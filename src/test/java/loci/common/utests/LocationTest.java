/*
 * #%L
 * Common package for I/O and related utilities
 * %%
 * Copyright (C) 2005 - 2016 Open Microscopy Environment:
 *   - Board of Regents of the University of Wisconsin-Madison
 *   - Glencoe Software, Inc.
 *   - University of Dundee
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package loci.common.utests;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

import loci.common.Location;

import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Unit tests for the loci.common.Location class.
 *
 * @see loci.common.Location
 */
public class LocationTest {

  private static final boolean IS_WINDOWS = System.getProperty("os.name").startsWith("Windows");

  // -- Fields --

  private enum LocalRemoteType {
    LOCAL,
    HTTP,
    S3,
  };

  private Location[] files;
  private Location[] rootFiles;
  private boolean[] exists;
  private boolean[] isDirectory;
  private boolean[] isHidden;
  private String[] mode;
  private LocalRemoteType[] isRemote;
  private boolean isOnline;
  private boolean canAccessS3;

  // -- Setup methods --

  @BeforeClass
  public void setup() throws IOException {
    File tmpDirectory = new File(System.getProperty("java.io.tmpdir"),
      System.currentTimeMillis() + "-location-test");
    boolean success = tmpDirectory.mkdirs();
    tmpDirectory.deleteOnExit();

    File hiddenFile = File.createTempFile(".hiddenTest", null, tmpDirectory);
    hiddenFile.deleteOnExit();

    File invalidFile = File.createTempFile("invalidTest", null, tmpDirectory);
    String invalidPath = invalidFile.getAbsolutePath();
    invalidFile.delete();

    File validFile = File.createTempFile("validTest", null, tmpDirectory);
    validFile.deleteOnExit();

    files = new Location[] {
      new Location(validFile.getAbsolutePath()),
      new Location(invalidPath),
      new Location(tmpDirectory),
      new Location("http://www.openmicroscopy.org/"),
      new Location("https://www.openmicroscopy.org/"),
      new Location("https://www.openmicroscopy.org/nonexisting"),
      new Location("https://www.openmicroscopy.org/nonexisting/:/+/symbols"),
      new Location(hiddenFile),
      new Location("s3+http://localhost:31836/bucket-dne"),
      new Location("s3+http://localhost:31836/bioformats.test.public"),
      new Location("s3+http://localhost:31836/bioformats.test.public/single-channel.ome.tiff"),
      new Location("s3+http://localhost:31836/bioformats.test.private/single-channel.ome.tiff"),
      new Location("s3+http://accesskey:secretkey@localhost:31836/bioformats.test.private/single-channel.ome.tiff")
    };

    rootFiles = new Location[] {
      new Location("/"),
      new Location("https://www.openmicroscopy.org"),
      new Location("s3://s3.example.org"),
    };

    exists = new boolean[] {
      true,
      false,
      true,
      true,
      true,
      false,
      false,
      true,
      false,
      true,
      true,
      false,
      true,
    };

    isDirectory = new boolean[] {
      false,
      false,
      true,
      false,
      false,
      false,
      false,
      false,
      false,
      true,
      false,
      false,
      false,
    };

    isHidden = new boolean[] {
      false,
      false,
      false,
      false,
      false,
      false,
      false,
      true,
      false,
      false,
      false,
      false,
      false,
    };

    mode = new String[] {
      "rw",
      "",
      "rw",
      "r",
      "r",
      "",
      "",
      "rw",
      "",
      "r",
      "r",
      "",
      "r",
    };

    isRemote = new LocalRemoteType[] {
      LocalRemoteType.LOCAL,
      LocalRemoteType.LOCAL,
      LocalRemoteType.LOCAL,
      LocalRemoteType.HTTP,
      LocalRemoteType.HTTP,
      LocalRemoteType.HTTP,
      LocalRemoteType.HTTP,
      LocalRemoteType.LOCAL,
      LocalRemoteType.S3,
      LocalRemoteType.S3,
      LocalRemoteType.S3,
      LocalRemoteType.S3,
      LocalRemoteType.S3,
    };
  }

  @BeforeClass
  public void checkIfOnline() throws IOException {
    try {
      new Socket("www.openmicroscopy.org", 80).close();
      isOnline = true;
    } catch (IOException e) {
      isOnline = false;
    }
    try {
      new Socket("localhost", 31836).close();
      canAccessS3 = true;
    } catch (IOException e) {
      canAccessS3 = false;
    }

    if (!isOnline) {
      System.err.println("WARNING: online tests are disabled!");
    }
    if (!canAccessS3) {
      System.err.println("WARNING: S3 tests are disabled!");
    }
  }

  private void failIfOffline(int i) throws SkipException {
    assertFalse("must be online to test " + files[i].getName(),
       isRemote[i] == LocalRemoteType.HTTP && !isOnline);
  }

  private void failIfS3Offline(int i) throws SkipException {
    assertFalse("Server not found, run `cd location-server && ./start-location.sh`: " + files[i].getName(),
      isRemote[i] == LocalRemoteType.S3 && !canAccessS3);
  }

  // -- Tests --
  // Order of assertEquals parameters is assertEquals(message, expected, actual)

  @Test
  public void testReadWriteMode() {
    for (int i=0; i<files.length; i++) {
      failIfOffline(i);
      failIfS3Offline(i);
      String msg = files[i].getName();
      assertEquals(msg, mode[i].contains("r"), files[i].canRead());
      assertEquals(msg, mode[i].contains("w"), files[i].canWrite());
    }
  }

  @Test
  public void testAbsolute() {
    for (Location file : files) {
      assertEquals(file.getName(), file.getAbsoluteFile().getAbsolutePath(), file.getAbsolutePath());
    }
  }

  @Test
  public void testExists() {
    for (int i=0; i<files.length; i++) {
      failIfOffline(i);
      failIfS3Offline(i);
      assertEquals(files[i].getName(), exists[i], files[i].exists());
    }
  }

  @Test
  public void testCanonical() throws IOException {
    for (Location file : files) {
      assertEquals(file.getName(), file.getCanonicalFile().getAbsolutePath(), file.getCanonicalPath());
    }
  }

  @Test
  public void testParent() {
    for (Location file : files) {
      assertEquals(file.getName(), file.getParentFile().getAbsolutePath(), file.getParent());
    }
  }

  @Test
  public void testParentRoot() {
    for (Location file : rootFiles) {
      assertEquals(file.getName(), null, file.getParent());
    }
  }

  @Test
  public void testIsDirectory() {
    for (int i=0; i<files.length; i++) {
      failIfS3Offline(i);
      assertEquals(files[i].getName(), isDirectory[i], files[i].isDirectory());
    }
  }

  @Test
  public void testIsFile() {
    for (int i=0; i<files.length; i++) {
      failIfOffline(i);
      failIfS3Offline(i);
      assertEquals(files[i].getName(), !isDirectory[i] && exists[i], files[i].isFile());
    }
  }

  @Test
  public void testIsHidden() {
    for (int i=0; i<files.length; i++) {
      assertEquals(files[i].getName(), isHidden[i] || IS_WINDOWS, files[i].isHidden() || IS_WINDOWS);
    }
  }

  @Test
  public void testListFiles() {
    for (int i=0; i<files.length; i++) {
      String[] completeList = files[i].list();
      String[] unhiddenList = files[i].list(true);
      Location[] fileList = files[i].listFiles();

      if (!files[i].isDirectory()) {
        assertEquals(files[i].getName(), null, completeList);
        assertEquals(files[i].getName(), null, unhiddenList);
        assertEquals(files[i].getName(), null, fileList);
        continue;
      }

      assertEquals(files[i].getName(), fileList.length, completeList.length);

      List<String> complete = Arrays.asList(completeList);
      for (String child : unhiddenList) {
        assertEquals(files[i].getName(), true, complete.contains(child));
        assertEquals(files[i].getName(), false, new Location(files[i], child).isHidden());
      }

      for (int f=0; f<fileList.length; f++) {
        assertEquals(files[i].getName(), completeList[f], fileList[f].getName());
      }
    }
  }

  @Test
  public void testToURL() throws IOException {
    for (Location file : files) {
      String path = file.getAbsolutePath();
      if (!path.contains("://")) {
        if (IS_WINDOWS) {
          path = "file:/" + path;
        }
        else {
          path = "file://" + path;
        }
      }
      if (file.isDirectory() && !path.endsWith(File.separator)) {
        path += File.separator;
      }
      try {
        assertEquals(file.getName(), new URL(path), file.toURL());
      } catch (MalformedURLException e) {
        assertEquals(path, true, path.contains("s3+http://"));
      }
    }
  }

  @Test
  public void testToString() {
    for (Location file : files) {
      assertEquals(file.getName(), file.getAbsolutePath(), file.toString());
    }
  }

}
