package uitest.NC3UITests;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTabItem;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTree;

import uitest.AbstractWindowTest;

@Tag("ui")
@Tag("integration")
public class TestNCTreeView extends AbstractWindowTest {

    @Test
    public void testRoy_attributes()
    {
        String[][] expectedData = {{""}, {""}};
        SWTBotShell tableShell  = null;
        String filename         = "Roy.nc";
        String groupname        = "/";
        String datasetName      = "";
        File hdfFile            = openFile(filename, FILE_MODE.READ_ONLY);

        try {
            SWTBotTree filetree = bot.tree();

            checkFileTree(filetree, "testRoy_attributes()", 2, filename);

            // Test metadata
            SWTBotTabItem tabItem = openMetadataTab(filetree, filename, groupname, "General Object Info");
            tabItem.activate();

            String val = bot.textWithLabel("Name: ").getText();
            assertTrue(val.equals(groupname), constructWrongValueMessage("testRoy_attributes()", "wrong name",
                                                                         groupname, val)); // Test group name
        }
        catch (Exception ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }
        catch (AssertionError ae) {
            ae.printStackTrace();
            fail(ae.getMessage());
        }
        finally {
            closeShell(tableShell);

            try {
                closeFile(hdfFile, false);
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }
    @Test
    public void testRoy()
    {
        SWTBotShell tableShell = null;
        String filename        = "Roy.nc";
        String datasetName     = "/";
        File hdfFile           = openFile(filename, FILE_MODE.READ_ONLY);

        try {
            SWTBotTree filetree = bot.tree();

            checkFileTree(filetree, "testRoy()", 3, filename);

            // Test metadata
            SWTBotTabItem tabItem = openMetadataTab(filetree, filename, datasetName, "General Object Info");
            tabItem.activate();

            String val = bot.textWithLabel("Name: ").getText();
            assertTrue(
                val.equals(datasetName),
                constructWrongValueMessage("testRoy()", "wrong name", datasetName, val)); // Test dataset name
        }
        catch (Exception ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }
        catch (AssertionError ae) {
            ae.printStackTrace();
            fail(ae.getMessage());
        }
        finally {
            closeShell(tableShell);

            try {
                closeFile(hdfFile, false);
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }
}
