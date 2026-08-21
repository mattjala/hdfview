/*****************************************************************************
 * Copyright by The HDF Group.                                               *
 * All rights reserved.                                                      *
 *                                                                           *
 * This file is part of the HDF Java Products distribution.                  *
 * The full copyright notice, including terms governing use, modification,   *
 * and redistribution, is contained in the COPYING file, which can be found  *
 * at the root of the source code distribution tree,                         *
 * or in https://www.hdfgroup.org/licenses.                                  *
 * If you do not have access to either file, you may request a copy from     *
 * help@hdfgroup.org.                                                        *
 ****************************************************************************/

package uitest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import static org.eclipse.swtbot.swt.finder.matchers.WidgetMatcherFactory.allOf;
import static org.eclipse.swtbot.swt.finder.matchers.WidgetMatcherFactory.widgetOfType;
import static org.eclipse.swtbot.swt.finder.matchers.WidgetMatcherFactory.withRegex;

import java.io.File;
import java.lang.reflect.Array;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import hdf.HDFVersions;
import hdf.view.HDFView;

import org.hamcrest.Matcher;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.eclipse.nebula.widgets.nattable.NatTable;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Monitor;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swtbot.nebula.nattable.finder.widgets.Position;
import org.eclipse.swtbot.nebula.nattable.finder.widgets.SWTBotNatTable;
import org.eclipse.swtbot.swt.finder.SWTBot;
import org.eclipse.swtbot.swt.finder.exceptions.WidgetNotFoundException;
import org.eclipse.swtbot.swt.finder.utils.SWTBotPreferences;
import org.eclipse.swtbot.swt.finder.waits.Conditions;
import org.eclipse.swtbot.swt.finder.widgets.AbstractSWTBot;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotCanvas;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotMenu;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTabItem;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTable;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotText;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTree;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;

import uitest.AbstractWindowTest.DataRetrieverFactory.TableDataRetriever;
import uitest.AbstractWindowTest.FILE_MODE;

public abstract class AbstractWindowTest {
    private static final Logger log = LoggerFactory.getLogger(AbstractWindowTest.class);

    protected static String HDF5VERSION = "HDF5 " + HDFVersions.getPropertyVersionHDF5();
    protected static String HDF4VERSION = "HDF " + HDFVersions.getPropertyVersionHDF4();
    // the version of the HDFViewer
    protected static String VERSION = HDFVersions.getPropertyVersionView();

    protected static String workDir = System.getProperty("hdfview.workdir");

    protected static SWTBot bot;

    protected static Thread uiThread;

    protected static Shell shell;

    private static final CyclicBarrier swtBarrier = new CyclicBarrier(2);

    /*
     * Upper bound on how long a test will wait for the main window to come up. Without a bound,
     * a failed application launch parks every @BeforeEach on swtBarrier forever - JUnit applies
     * no default timeout to @BeforeEach - so the fork wedges and CI reports a bare job timeout
     * with no surefire output at all.
     */
    private static final int APP_STARTUP_TIMEOUT_SECONDS = 60;

    /* Why the UI thread died, if it did, so that waiters can report the real cause. */
    private static volatile Throwable appStartupFailure = null;

    /*
     * How long any single bot.waitUntil() will block before giving up. Set explicitly rather
     * than inheriting the SWTBot default so that a widget which never appears costs a bounded,
     * known amount of time instead of whatever the library happens to default to.
     */
    private static final long SWTBOT_TIMEOUT_MS = 10000L;

    private static int TEST_DELAY = 10;

    private static int open_files = 0;

    protected static Rectangle monitorBounds;

    protected TestInfo testInfo;

    protected static enum FILE_MODE { READ_ONLY, READ_WRITE, MULTI_READ_ONLY }

    private static final String objectShellTitleRegex = ".*at.*\\[.*in.*\\]";

    @BeforeEach
    public final void setupSWTBot(TestInfo testInfo) throws InterruptedException
    {
        this.testInfo = testInfo;
        // synchronize with the thread opening the shell
        awaitAppWindow();
        bot = new SWTBot();

        /*
         * Each rendezvous with the ui thread hands back a brand new HDFView and shell - its
         * while(true) loop builds one per trip through the barrier - so no file is open yet.
         * open_files is static and was never reset, so one failed test left the count high and
         * every later test in the class then waited for a file tree row that could not arrive,
         * turning a single failure into a whole class of misleading ones.
         */
        open_files = 0;

        SWTBotPreferences.TIMEOUT        = SWTBOT_TIMEOUT_MS;
        SWTBotPreferences.PLAYBACK_DELAY = TEST_DELAY;
        Display.getDefault().syncExec(new Runnable() {
            @Override
            public void run()
            {
                shell.forceActive();
            }
        });
    }

    /**
     * Rendezvous with the UI thread, bounded in time. Reports a test failure rather than
     * blocking indefinitely when the main window never opens. Once the barrier is tripped or
     * broken it stays broken, so every subsequent test fails fast with the same cause instead
     * of each one paying the timeout again.
     */
    private static void awaitAppWindow() throws InterruptedException
    {
        try {
            swtBarrier.await(APP_STARTUP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
        catch (TimeoutException e) {
            fail("HDFView main window did not open within " + APP_STARTUP_TIMEOUT_SECONDS + " seconds",
                 appStartupFailure);
        }
        catch (BrokenBarrierException e) {
            fail("HDFView UI thread died before the main window opened", appStartupFailure);
        }
    }

    @AfterEach
    public void closeShell() throws InterruptedException
    {
        // Nothing to tear down if the window never opened. Bail out before touching Display:
        // Display.getDefault() *creates* a display when none exists, and on a machine with no
        // usable X server that call blocks forever inside the native gdk_threads_enter(). It is
        // not interruptible, so neither the JUnit timeout nor a thread interrupt can break it
        // out - the fork wedges and only the surefire fork timeout can end it.
        if (shell == null || shell.isDisposed())
            return;

        // close the shell
        Display.getDefault().syncExec(new Runnable() {
            @Override
            public void run()
            {
                shell.close();
            }
        });
    }

    public void checkOpenFiles()
    {
        if (open_files > 0) {
            String failMsg =
                "Test " + testInfo.getDisplayName() + " still had " + open_files + " files open!";

            open_files = 0;

            fail(failMsg);
        }

        open_files = 0;
    }

    @BeforeAll
    public static void setupApp()
    {
        clearRemovePropertyFile();

        if (uiThread == null) {
            uiThread = new Thread(new Runnable() {
                @Override
                public void run()
                {
                    try {
                        Vector<File> fList = new Vector<>();
                        String rootDir     = System.getProperty("hdfview.rootdir");
                        if (rootDir == null)
                            rootDir = System.getProperty("user.dir");
                        String startDir = System.getProperty("hdfview.workdir");

                        int W = 800, H = 600, X = 0, Y = 0;

                        while (true) {
                            // open and layout the shell
                            HDFView window = new HDFView(rootDir, startDir);

                            // Set the testing state to handle the problem with testing
                            // of native dialogs
                            window.setTestState(true);

                            shell = window.openMainWindow(fList, W, H, X, Y);

                            // Force the HDFView window to open fullscreen on the active
                            // monitor so that certain tests don't encounter weird issues
                            // due to the window being too small
                            Monitor[] monitors    = shell.getDisplay().getMonitors();
                            Monitor activeMonitor = null;

                            Rectangle r = shell.getBounds();
                            for (int i = 0; i < monitors.length; i++) {
                                if (monitors[i].getBounds().intersects(r)) {
                                    activeMonitor = monitors[i];
                                }
                            }

                            monitorBounds = activeMonitor.getBounds();

                            shell.setBounds(monitorBounds);

                            // wait for the test setup
                            swtBarrier.await();

                            // run the event loop
                            window.runMainWindow();
                        }
                    }
                    catch (Throwable t) {
                        // Record the real cause and break the barrier so that any test parked
                        // in awaitAppWindow() fails immediately with this stack trace, rather
                        // than waiting out the startup timeout for a window that will never
                        // arrive. Throwable, not Exception: a linkage or native-library error
                        // during SWT startup is exactly the case that used to hang CI.
                        appStartupFailure = t;
                        t.printStackTrace();
                        swtBarrier.reset();
                    }

                    if (shell != null) {
                        Display.getDefault().syncExec(new Runnable() {
                            @Override
                            public void run()
                            {
                                shell.getDisplay().dispose();
                            }
                        });
                    }
                }
            });
            uiThread.setDaemon(true);
            uiThread.start();
        }
    }

    private static void clearRemovePropertyFile()
    {
        // the local property file name
        // look for the property file at the use home directory
        String fn = ".hdfview" + VERSION;

        File prop_file = new File(workDir, fn);
        if (prop_file.exists()) {
            prop_file.delete();
        }
    }

    /**
     * Close a dialog we are done with, swallowing anything it throws on the way out.
     *
     * Cleanup runs on the failure path, and an exception thrown from a finally block replaces
     * the one already propagating - so an unguarded close() here would discard the very
     * failure the caller just took care to report, substituting a bare "Timed out waiting for
     * Shell ... to close". SWTBotShell.close() ends in a waitUntil, so it can absolutely throw:
     * a modal error dialog raised over this one keeps it open until the SWTBot timeout.
     */
    private static void closeQuietly(SWTBotShell theShell)
    {
        try {
            if (theShell != null && theShell.isOpen())
                theShell.close();
        }
        catch (Throwable ignored) {
            /* the failure being reported by the caller matters more than this one */
        }
    }

    protected File openFile(String name, FILE_MODE openMode)
    {
        SWTBotShell fileNameShell = null;
        File hdf_file             = new File(workDir, name);
        log.trace("openFile workDir is {}, name is {}", workDir, name);

        try {
            SWTBotMenu fileMenuItem   = bot.menu().menu("File");
            SWTBotMenu openasMenuItem = fileMenuItem.menu("Open As");
            if (openMode == FILE_MODE.MULTI_READ_ONLY)
                openasMenuItem.menu("SWMR Read-Only").click();
            else if (openMode == FILE_MODE.READ_ONLY)
                openasMenuItem.menu("Read-Only").click();
            else
                openasMenuItem.menu("Read/Write").click();

            fileNameShell = bot.shell("Enter a file name");
            fileNameShell.activate();
            bot.waitUntil(Conditions.shellIsActive(fileNameShell.getText()));

            SWTBotText text = fileNameShell.bot().text();
            text.setText(hdf_file.getName());

            String val = text.getText();
            assertTrue(val.equals(hdf_file.getName()), "openFile() wrong file name: expected '" +
                                                           hdf_file.getName() + "' but was '" + val + "'");

            fileNameShell.bot().button("   &OK   ").click();
            bot.waitUntil(Conditions.shellCloses(fileNameShell));

            SWTBotTree filetree = bot.tree();
            bot.waitUntil(Conditions.treeHasRows(filetree, open_files + 1));

            /*
             * TODO: difference here between rowCount() and visibleRowCount(). Can't use
             * checkFileTree().
             */
            assertTrue(filetree.rowCount() == open_files + 1,
                       "openFile() filetree wrong row count: expected '" + String.valueOf(open_files) +
                           "' but was '" + filetree.rowCount() + "'");
            assertTrue(filetree.getAllItems()[open_files].getText().compareTo(hdf_file.getName()) == 0,
                       "openFile() filetree is missing file '" + hdf_file.getName() + "'");

            /*
             * Increment the open_files value last, in case an error occurs when opening a
             * file.
             */
            open_files++;
        }
        /*
         * Report rather than swallow. Both of these used to printStackTrace() and return the
         * File as if nothing had happened, so a failure here surfaced much later as an
         * unrelated-looking row-count or widget-not-found error in the caller. closeFile() and
         * testSamplePixel() below already fail() for the same reason.
         */
        catch (Exception ex) {
            fail("openFile() failed to open '" + name + "'", ex);
        }
        catch (AssertionError ae) {
            fail("openFile() failed to open '" + name + "'", ae);
        }
        finally {
            closeQuietly(fileNameShell);
        }
        log.trace("openFile  {}, open_files={}", name, open_files);

        return hdf_file;
    }

    protected File createFile(String name)
    {
        boolean hdf4Type = (name.lastIndexOf(".hdf") >= 0);
        boolean hdf5Type = (name.lastIndexOf(".h5") >= 0);

        File hdfFile = new File(workDir, name);
        log.trace("createFile workDir is {}, name is {}", workDir, name);
        if (hdfFile.exists())
            hdfFile.delete();

        SWTBotShell fileNameShell = null;

        try {
            SWTBotMenu fileMenuItem    = bot.menu().menu("File");
            SWTBotMenu fileNewMenuItem = fileMenuItem.menu("New");
            if (hdf4Type)
                fileNewMenuItem.menu("HDF4").click();
            else if (hdf5Type)
                fileNewMenuItem.menu("HDF5").click();
            else
                throw new IllegalArgumentException("unknown file type");

            fileNameShell = bot.shell("Enter a file name");
            fileNameShell.activate();
            bot.waitUntil(Conditions.shellIsActive(fileNameShell.getText()));

            SWTBotText text = fileNameShell.bot().text();
            text.setText(name);

            String val = text.getText();
            assertTrue(val.equals(name),
                       "createFile() wrong file name: expected '" + name + "' but was '" + val + "'");

            fileNameShell.bot().button("   &OK   ").click();
            bot.waitUntil(Conditions.shellCloses(fileNameShell));

            assertTrue(hdfFile.exists(), "createFile() File '" + hdfFile + "' not created");
            open_files++;
        }
        /*
         * Report rather than swallow. Both of these used to printStackTrace() and return the
         * File as if nothing had happened, so a failure here surfaced much later as an
         * unrelated-looking row-count or widget-not-found error in the caller. closeFile() and
         * testSamplePixel() below already fail() for the same reason.
         */
        catch (Exception ex) {
            fail("createFile() failed to create '" + name + "'", ex);
        }
        catch (AssertionError ae) {
            fail("createFile() failed to create '" + name + "'", ae);
        }
        finally {
            /*
             * createFile() had no cleanup at all. That was survivable while it swallowed
             * failures and returned; now that it throws, an abort here would leave the modal
             * "Enter a file name" shell up across @AfterEach, where closeShell() syncExecs a
             * close on the main window underneath it - and the ui thread may never loop round
             * to build the next window, taking the rest of the class with it.
             */
            closeQuietly(fileNameShell);
        }
        log.trace("createFile  {}, open_files={}", name, open_files);

        return hdfFile;
    }

    /**
     * Bring the main window forward and wait until SWT agrees it is the active shell.
     *
     * SWTBot resolves widgets against whatever Display.getActiveShell() returns, and closing a
     * data window leaves that null for a short while - observed directly with a shell dump:
     * exactly one shell present, visible and undisposed, yet getActiveShell() returned null. A
     * lookup landing in that gap fails with "Could not find widget matching: (of type 'Tree')"
     * or "The widget was null" even though the widget is sitting right there. Every one of the
     * 42 Tree failures in the first Linux CI run came through closeFile() this way.
     *
     * Waiting on the main shell by identity is what makes this deterministic; bot.shells()[0]
     * is whatever order Display.getShells() happens to return, which is not a guarantee.
     */
    protected static SWTBotShell activateMainShell()
    {
        SWTBotShell mainShell = new SWTBotShell(shell);
        mainShell.activate();
        bot.waitUntil(Conditions.shellIsActive(mainShell.getText()));
        return mainShell;
    }

    protected void closeFile(File hdfFile, boolean deleteFile)
    {
        try {
            SWTBotShell mainShell = activateMainShell();
            SWTBotTree filetree   = mainShell.bot().tree();
            log.trace("closeFile {}, open_files={}", hdfFile.getName(), open_files);

            /*
             * Collapse the file's node before clicking it. HDFView takes the file to close from
             * DefaultTreeView.selectedFile, which is maintained by the tree's mouseUp handler -
             * and that handler sets selectedFile to null outright when the click does not land
             * on a TreeItem. After a test has expanded much of the tree the file's own row can
             * be scrolled out of reach, so the synthesized click lands on empty space and
             * silently clears the selection. Collapsing puts the file back on the first row.
             */
            SWTBotTreeItem fileItem = filetree.getTreeItem(hdfFile.getName());
            if (fileItem.isExpanded())
                fileItem.collapse();

            filetree.select(hdfFile.getName());
            fileItem.click();

            SWTBotMenu fileMenuItem = bot.menu().menu("File");
            fileMenuItem.menu("Close").click();

            /*
             * A Close with no file selected is answered with a beep and an error dialog titled
             * "<main window title> - Close" (Tools.showError), and the file stays open. Say so.
             * Without this the only symptom is a row count that never drops, which reports that
             * something went wrong but nothing about what.
             */
            for (SWTBotShell openShell : bot.shells()) {
                if (openShell.isOpen() && openShell.getText().endsWith(" - Close")) {
                    openShell.close();
                    fail("closeFile() HDFView declined to close '" + hdfFile.getName() +
                         "' and raised its \"" + openShell.getText() +
                         "\" error dialog - the file tree selection was lost before File > Close");
                }
            }

            if (deleteFile) {
                if (hdfFile.exists()) {
                    assertTrue(hdfFile.delete(), "closeFile() File '" + hdfFile + "' not deleted");
                    assertFalse(hdfFile.exists(), "closeFile() File '" + hdfFile + "' not gone");
                }
            }
            log.trace("closeFile after open_files={}", open_files);

            if (open_files > 0) {
                /*
                 * Wait for the close to land before judging it. The tree is rebuilt on the UI
                 * thread after File > Close returns, so reading rowCount() on the next statement
                 * races that rebuild - a race a loaded CI runner loses more often than a dev box.
                 * The waitUntil for this condition used to sit below the assertion, where it
                 * could never help. On timeout, fall through so the assertion reports the count.
                 */
                int expectedRows = open_files - 1;
                try {
                    bot.waitUntil(Conditions.treeHasRows(filetree, expectedRows));
                }
                catch (org.eclipse.swtbot.swt.finder.widgets.TimeoutException te) {
                    /* deliberately ignored - the assertion below gives the better message */
                }

                assertTrue(filetree.rowCount() == expectedRows,
                           constructWrongValueMessage("closeFile()", "filetree wrong row count",
                                                      String.valueOf(expectedRows),
                                                      String.valueOf(filetree.rowCount())));
                open_files--;
            }

            bot.waitUntil(Conditions.treeHasRows(filetree, open_files));
            log.trace("closeFile after open_files={}", open_files);
        }
        catch (Exception ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }
        catch (AssertionError ae) {
            ae.printStackTrace();
            fail(ae.getMessage());
        }
    }

    protected void checkFileTree(SWTBotTree tree, String funcName, int expectedRowCount, String filename)
        throws IllegalArgumentException, AssertionError
    {
        if (tree == null)
            throw new IllegalArgumentException("SWTBotTree parameter is null");
        if (filename == null)
            throw new IllegalArgumentException("file name parameter is null");
        bot.sleep(500);

        String expectedRowCountStr = String.valueOf(expectedRowCount);
        int visibleRowCount        = tree.visibleRowCount();
        assertTrue(visibleRowCount == expectedRowCount,
                   constructWrongValueMessage(funcName, "filetree wrong row count", expectedRowCountStr,
                                              String.valueOf(visibleRowCount)));

        String curFilename = tree.getAllItems()[0].getText();
        assertTrue(curFilename.compareTo(filename) == 0,
                   constructWrongValueMessage(funcName, "filetree is missing file", filename, curFilename));
    }

    protected void testSamplePixel(final int theX, final int theY, String requiredValue)
    {
        try {
            SWTBotShell botshell           = bot.activeShell();
            SWTBot thisbot                 = botshell.bot();
            final SWTBotCanvas imageCanvas = thisbot.canvas(1);

            // Make sure Show Values is selected
            SWTBotMenu imageMenuItem      = thisbot.menu().menu("Image");
            SWTBotMenu showValuesMenuItem = imageMenuItem.menu("Show Values");
            if (!showValuesMenuItem.isChecked()) {
                showValuesMenuItem.click();
            }

            Display.getDefault().syncExec(new Runnable() {
                @Override
                public void run()
                {
                    imageCanvas.widget.notifyListeners(SWT.MouseMove, new Event() {
                        {
                            x = theX;
                            y = theY;
                        }
                    });
                }
            });

            String val = thisbot.text().getText();
            assertTrue(
                val.equals(requiredValue),
                constructWrongValueMessage("testSamplePixel()", "wrong pixel value", requiredValue, val));
        }
        catch (Exception ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }
        catch (AssertionError ae) {
            ae.printStackTrace();
            fail(ae.getMessage());
        }
    }

    protected SWTBotTable openAttributeTable(SWTBotTree tree, String filename, String objectName)
    {
        SWTBotTreeItem fileItem = tree.getTreeItem(filename);

        SWTBotTreeItem foundObject = locateItemByPath(fileItem, objectName);
        foundObject.click();

        SWTBotTabItem tabItem = bot.tabItem("Object Attribute Info");
        tabItem.activate();

        return new SWTBotTable(bot.widget(widgetOfType(Table.class)));
    }

    protected SWTBotTabItem openMetadataTab(SWTBotTree tree, String filename, String objectName,
                                            String tabName)
    {
        SWTBotTreeItem fileItem = tree.getTreeItem(filename);

        SWTBotTreeItem foundObject = locateItemByPath(fileItem, objectName);
        foundObject.click();

        return bot.tabItem(tabName);
    }

    protected SWTBotShell openAttributeObject(SWTBotTable attrTable, String objectName, int rowIndex)
    {
        attrTable.doubleClick(rowIndex, 0);

        return openDataObject(objectName);
    }

    protected SWTBotShell openAttributeContext(SWTBotTable attrTable, String objectName, int rowIndex)
    {
        attrTable.click(rowIndex, 0);
        attrTable.contextMenu().contextMenu("View/Edit Attribute Value").click();

        return openDataObject(objectName);
    }

    protected SWTBotShell openTreeviewObject(SWTBotTree tree, String filename, String objectName)
    {
        SWTBotTreeItem fileItem = tree.getTreeItem(filename);

        SWTBotTreeItem foundObject = locateItemByPath(fileItem, objectName);
        foundObject.click();
        foundObject.contextMenu().contextMenu("Open").click();

        return openDataObject(objectName);
    }

    protected SWTBotShell openDataObject(String objectName)
    {
        String strippedObjectName = objectName;
        int slashLoc              = objectName.lastIndexOf('/');
        if (slashLoc >= 0) {
            strippedObjectName = objectName.substring(slashLoc + 1);
        }

        Matcher<Shell> classMatcher = widgetOfType(Shell.class);
        Matcher<Shell> regexMatcher = withRegex(strippedObjectName + objectShellTitleRegex);
        @SuppressWarnings("unchecked")
        Matcher<Shell> shellMatcher = allOf(classMatcher, regexMatcher);
        bot.waitUntil(Conditions.waitForShell(shellMatcher));

        final SWTBotShell botShell = new SWTBotShell(bot.widget(shellMatcher));

        botShell.activate();
        bot.waitUntil(Conditions.shellIsActive(botShell.getText()));

        /*
         * Due to testing issues where the values can't be retrieved from non-visible
         * table columns, we ensure that the table Shell is always maximized.
         */
        Display.getDefault().syncExec(new Runnable() {
            @Override
            public void run()
            {
                botShell.widget.setMaximized(true);
            }
        });

        return botShell;
    }

    private SWTBotTreeItem locateItemByPath(SWTBotTreeItem startNode, String objPath)
    {
        StringTokenizer st  = new StringTokenizer(objPath, "/");
        SWTBotTreeItem node = startNode;

        while (st.hasMoreTokens()) {
            String nextToken = st.nextToken();
            node             = node.getNode(nextToken);
            if (node.getItems().length > 0)
                node.expand();
        }

        return node;
    }

    /*
     * A factory class to return concrete instances of TableDataRetriever classes,
     * which will retrieve the data value at the specified row and column position
     * in the given Table object.
     */
    public static class DataRetrieverFactory {

        public static TableDataRetriever getTableDataRetriever(AbstractSWTBot<?> tableObject, String funcName,
                                                               boolean noRegex)
        {
            if (tableObject == null)
                throw new IllegalArgumentException("AbstractSWTBot parameter is null");
            if (funcName == null)
                throw new IllegalArgumentException("function name parameter is null");

            if (tableObject instanceof SWTBotNatTable)
                return new NatTableDataRetriever((SWTBotNatTable)tableObject, funcName, noRegex);
            else
                return new SWTTableDataRetriever((SWTBotTable)tableObject, funcName, noRegex);
        }

        public static class TableDataRetriever {

            protected final StringBuilder sb;

            protected final String funcName;

            protected final boolean noRegex;

            public TableDataRetriever(String funcName, boolean noRegex)
            {
                this.funcName = funcName;
                this.noRegex  = noRegex;

                this.sb = new StringBuilder();
            }

            /*
             * Utility function to offset the table row position for extra header info.
             */
            public void setContainerHeaderOffset(int containerRowHeaderOffset, int containerColHeaderOffset)
            {
                throw new UnsupportedOperationException(
                    "subclasses must implement setContainerHeaderOffset()");
            }

            public void setPagingActive(boolean pagingActive)
            {
                throw new UnsupportedOperationException("subclasses must implement setPagingActive()");
            }

            /*
             * Utility function to compare a given table position against an expected value.
             */
            public void testTableLocation(int rowIndex, int colIndex, String expectedValRegex)
            {
                throw new UnsupportedOperationException("subclasses must implement testTableLocation()");
            }

            /*
             * Utility function wrapper around testTableLocations() for testing an entire
             * table.
             */
            public void testAllTableLocations(String[][] expectedValRegexArray)
            {
                testTableLocations(0, 0, expectedValRegexArray);
            }

            public void testTableLocations(int rowOffset, int colOffset, String[][] expectedValRegexArray)
            {
                int arrLen = Array.getLength(expectedValRegexArray);
                for (int i = 0; i < arrLen; i++) {
                    String[] nestedArray = (String[])Array.get(expectedValRegexArray, i);
                    int nestedLen        = Array.getLength(nestedArray);

                    for (int j = 0; j < nestedLen; j++)
                        testTableLocation(rowOffset + i, colOffset + j, (String)Array.get(nestedArray, j));
                }
            }
        }

        private static class NatTableDataRetriever extends TableDataRetriever {

            private final SWTBotNatTable table;
            private int containerRowHeaderOffset = 0;
            private int containerColHeaderOffset = 0;
            boolean pagingActive                 = false;
            Position lastVisibleCellPos =
                new Position(1 + containerRowHeaderOffset, 1 + containerColHeaderOffset);

            NatTableDataRetriever(SWTBotNatTable tableObj, String funcName, boolean noRegex)
            {
                super(funcName, noRegex);

                this.table = tableObj;
                log.trace("lastVisibleCellPos: row is {}, col is {}", lastVisibleCellPos.row,
                          lastVisibleCellPos.column);
            }

            @Override
            public void testTableLocation(int rowIndex, int colIndex, String expectedValRegex)
            {
                if (expectedValRegex == null)
                    throw new IllegalArgumentException("expected value string parameter is null");

                int textboxIndex = 0;
                if (pagingActive)
                    textboxIndex = 2;

                // TODO: temporary workaround until the solution below works.
                log.trace("rowIndex is {}, colIndex is {}", rowIndex, colIndex);
                lastVisibleCellPos = table.scrollViewport(lastVisibleCellPos, rowIndex, colIndex);
                log.trace("lastVisibleCellPos: row is {}, col is {}", lastVisibleCellPos.row,
                          lastVisibleCellPos.column);
                table.click(lastVisibleCellPos.row, lastVisibleCellPos.column);
                bot.sleep(50);
                String val = bot.shells()[1].bot().text(textboxIndex).getText();

                // Disabled until Data conversion can be figured out
                // String val = table.getCellDataValueByPosition(rowIndex, colIndex);

                sb.setLength(0);
                sb.append("wrong value at table index ")
                    .append("(")
                    .append(rowIndex)
                    .append(", ")
                    .append(colIndex)
                    .append(")");
                String errMsg = constructWrongValueMessage(funcName, sb.toString(), expectedValRegex, val);
                if (noRegex)
                    expectedValRegex = "\\Q" + expectedValRegex + "\\E";
                assertTrue(val.matches(expectedValRegex), errMsg);
            }

            @Override
            public void setPagingActive(boolean pagingActive)
            {
                this.pagingActive = pagingActive;
            }

            @Override
            public void setContainerHeaderOffset(int containerRowHeaderOffset, int containerColHeaderOffset)
            {
                this.containerRowHeaderOffset = containerRowHeaderOffset;
                this.containerColHeaderOffset = containerColHeaderOffset;
                lastVisibleCellPos = new Position(1 + containerRowHeaderOffset, 1 + containerColHeaderOffset);
            }
        }

        private static class SWTTableDataRetriever extends TableDataRetriever {

            private final SWTBotTable table;

            SWTTableDataRetriever(SWTBotTable tableObj, String funcName, boolean noRegex)
            {
                super(funcName, noRegex);

                this.table = tableObj;
            }

            @Override
            public void testTableLocation(int rowIndex, int colIndex, String expectedValRegex)
            {
                if (expectedValRegex == null)
                    throw new IllegalArgumentException("expected value string parameter is null");

                table.click(rowIndex, colIndex);
                String val = table.cell(rowIndex, colIndex);

                sb.setLength(0);
                sb.append("wrong value at table index ")
                    .append("(")
                    .append(rowIndex)
                    .append(", ")
                    .append(colIndex)
                    .append(")");
                String errMsg = constructWrongValueMessage(funcName, sb.toString(), expectedValRegex, val);
                if (noRegex)
                    expectedValRegex = "\\Q" + expectedValRegex + "\\E";
                assertTrue(val.matches(expectedValRegex), errMsg);
            }
        }
    }

    protected SWTBotNatTable getNatTable(SWTBotShell theShell)
    {
        return new SWTBotNatTable(theShell.bot().widget(widgetOfType(NatTable.class)));
    }

    protected final void closeShell(SWTBotShell theShell)
    {
        if (theShell == null || !theShell.isOpen())
            return;

        SWTBotMenu closeButton = null;
        try {
            closeButton = theShell.bot().menu("Close");
        }
        catch (WidgetNotFoundException ex) {
            closeButton = null;
        }

        if (closeButton != null) {
            closeButton.click();
            bot.waitUntil(Conditions.shellCloses(theShell));
        }
    }

    /*
     * Only useful when testing certain Menu items which open files in a different
     * manner than the openFile() method.
     */
    protected final void refreshOpenFileCount() { open_files = bot.tree().getAllItems().length; }

    /*
     * Only useful when testing certain Menu items which close files in a different
     * manner than the closeFile() method.
     */
    protected final void resetOpenFileCount() { open_files = 0; }

    protected static String constructWrongValueMessage(String methodName, String message, String expected,
                                                       String actual)
    {
        StringBuilder builder = new StringBuilder(methodName);
        builder.append(" " + message + ": expected '" + expected + "' but was '" + actual + "'");

        if (expected.equals(actual))
            builder.append(" - possible regex mismatch due to non-escaped characters \\^${}[]()*+?|<>-&");

        return builder.toString();
    }
}
