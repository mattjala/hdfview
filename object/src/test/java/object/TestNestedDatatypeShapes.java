package object;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import hdf.hdf5lib.H5;
import hdf.hdf5lib.HDF5Constants;
import hdf.object.Dataset;
import hdf.object.FileFormat;
import hdf.object.h5.H5File;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Read tests for nested datatype shapes not covered by the shared UI test files.
 *
 * Fixtures are built at test time rather than committed as binaries, so adding a
 * shape costs a few lines here. They are written with raw H5Dwrite/H5DwriteVL calls
 * against the JNI's buffer data model; that path does not reach
 * H5Datatype.allocateArray(), so the setup is independent of what is being read.
 */
@Tag("unit")
@DisplayName("Nested Datatype Shape Read Tests")
public class TestNestedDatatypeShapes {

    @TempDir
    static Path tempDir;

    private static H5File testFile;
    private static int openIDsAtStart;

    @BeforeAll
    static void createFile() throws Exception
    {
        openIDsAtStart = H5.getOpenIDCount();

        String path = tempDir.resolve("nested_shapes.h5").toString();
        long fid    = H5.H5Fcreate(path, HDF5Constants.H5F_ACC_TRUNC, HDF5Constants.H5P_DEFAULT,
                                   HDF5Constants.H5P_DEFAULT);
        try {
            writeVlenOfCompound(fid);
            writeVlenOfVarStr(fid);
            writeArrayOfVlenInt(fid);
            writeArrayOfArrayVarStr(fid);
            writeArrayOfArrayInt(fid);
            writeArrayOfCompoundVarStr(fid);
            writeCompoundOfVlenCompound(fid);
        }
        finally {
            H5.H5Fclose(fid);
        }

        testFile = (H5File)(new H5File()).createInstance(path, FileFormat.READ);
        testFile.open();
    }

    @AfterAll
    static void closeFile() throws Exception
    {
        if (testFile != null)
            testFile.close();

        assertEquals(openIDsAtStart, H5.getOpenIDCount(), "HDF5 identifiers leaked by this test class");
    }

    /** Variable-length string type; the caller closes it. */
    private static long varStrType() throws Exception
    {
        long tid = H5.H5Tcopy(HDF5Constants.H5T_C_S1);
        H5.H5Tset_size(tid, HDF5Constants.H5T_VARIABLE);
        return tid;
    }

    /** Compound {p:int, q:int}; the caller closes it. */
    private static long pqCompoundType() throws Exception
    {
        long tid = H5.H5Tcreate(HDF5Constants.H5T_COMPOUND, 8);
        H5.H5Tinsert(tid, "p", 0, HDF5Constants.H5T_NATIVE_INT);
        H5.H5Tinsert(tid, "q", 4, HDF5Constants.H5T_NATIVE_INT);
        return tid;
    }

    private static void writeDataset(long fid, String name, long tid, int nPoints, Object buf,
                                     boolean vlen) throws Exception
    {
        long sid = H5.H5Screate_simple(1, new long[] {nPoints}, null);
        long did = H5.H5Dcreate(fid, name, tid, sid, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT,
                                HDF5Constants.H5P_DEFAULT);
        try {
            if (vlen)
                H5.H5DwriteVL(did, tid, HDF5Constants.H5S_ALL, HDF5Constants.H5S_ALL,
                              HDF5Constants.H5P_DEFAULT, (Object[])buf);
            else
                H5.H5Dwrite(did, tid, HDF5Constants.H5S_ALL, HDF5Constants.H5S_ALL,
                            HDF5Constants.H5P_DEFAULT, buf);
        }
        finally {
            H5.H5Dclose(did);
            H5.H5Sclose(sid);
        }
    }

    private static ArrayList<Object> list(Object... items)
    {
        ArrayList<Object> out = new ArrayList<>();
        for (Object item : items)
            out.add(item);
        return out;
    }

    // ---- fixtures -------------------------------------------------------------

    /** VLEN of COMPOUND{p,q}. */
    private static void writeVlenOfCompound(long fid) throws Exception
    {
        long inner = pqCompoundType();
        long tid   = H5.H5Tvlen_create(inner);
        try {
            Object[] buf = {list(list(10, 11), list(20, 21)), list(list(30, 31))};
            writeDataset(fid, "vlen_of_compound", tid, 2, buf, true);
        }
        finally {
            H5.H5Tclose(tid);
            H5.H5Tclose(inner);
        }
    }

    /** VLEN of variable-length string. */
    private static void writeVlenOfVarStr(long fid) throws Exception
    {
        long vs  = varStrType();
        long tid = H5.H5Tvlen_create(vs);
        try {
            Object[] buf = {list("a0", "a1"), list("b0")};
            writeDataset(fid, "vlen_of_varstr", tid, 2, buf, true);
        }
        finally {
            H5.H5Tclose(tid);
            H5.H5Tclose(vs);
        }
    }

    /** ARRAY[2] of VLEN of int. */
    private static void writeArrayOfVlenInt(long fid) throws Exception
    {
        long vt  = H5.H5Tvlen_create(HDF5Constants.H5T_NATIVE_INT);
        long tid = H5.H5Tarray_create(vt, 1, new long[] {2});
        try {
            Object[] buf = {list(list(1, 2), list(3)), list(list(4), list(5, 6))};
            writeDataset(fid, "array_of_vlen_int", tid, 2, buf, true);
        }
        finally {
            H5.H5Tclose(tid);
            H5.H5Tclose(vt);
        }
    }

    /** ARRAY[2] of ARRAY[3] of variable-length string. */
    private static void writeArrayOfArrayVarStr(long fid) throws Exception
    {
        long vs    = varStrType();
        long inner = H5.H5Tarray_create(vs, 1, new long[] {3});
        long tid   = H5.H5Tarray_create(inner, 1, new long[] {2});
        try {
            Object[] buf = {list(list("n000", "n001", "n002"), list("n010", "n011", "n012")),
                            list(list("n100", "n101", "n102"), list("n110", "n111", "n112"))};
            writeDataset(fid, "array_of_array_varstr", tid, 2, buf, true);
        }
        finally {
            H5.H5Tclose(tid);
            H5.H5Tclose(inner);
            H5.H5Tclose(vs);
        }
    }

    /** ARRAY[2] of ARRAY[3] of int. */
    private static void writeArrayOfArrayInt(long fid) throws Exception
    {
        long inner = H5.H5Tarray_create(HDF5Constants.H5T_NATIVE_INT, 1, new long[] {3});
        long tid   = H5.H5Tarray_create(inner, 1, new long[] {2});
        try {
            int[] buf = {0, 1, 2, 10, 11, 12, 100, 101, 102, 110, 111, 112};
            writeDataset(fid, "array_of_array_int", tid, 2, buf, false);
        }
        finally {
            H5.H5Tclose(tid);
            H5.H5Tclose(inner);
        }
    }

    /** ARRAY[2] of COMPOUND{n:int, s:varstr}. */
    private static void writeArrayOfCompoundVarStr(long fid) throws Exception
    {
        long vs   = varStrType();
        long strSz = H5.H5Tget_size(vs);
        long cmpd = H5.H5Tcreate(HDF5Constants.H5T_COMPOUND, 8 + strSz);
        H5.H5Tinsert(cmpd, "n", 0, HDF5Constants.H5T_NATIVE_INT);
        H5.H5Tinsert(cmpd, "s", 8, vs);
        long tid = H5.H5Tarray_create(cmpd, 1, new long[] {2});
        try {
            Object[] buf = {list(list(0, "x0"), list(1, "x1")), list(list(2, "y0"), list(3, "y1"))};
            writeDataset(fid, "array_of_compound_varstr", tid, 2, buf, true);
        }
        finally {
            H5.H5Tclose(tid);
            H5.H5Tclose(cmpd);
            H5.H5Tclose(vs);
        }
    }

    /** COMPOUND{id:int, nested:VLEN of COMPOUND{p,q}}. */
    private static void writeCompoundOfVlenCompound(long fid) throws Exception
    {
        long inner  = pqCompoundType();
        long vt     = H5.H5Tvlen_create(inner);
        long vtSize = H5.H5Tget_size(vt);
        long tid    = H5.H5Tcreate(HDF5Constants.H5T_COMPOUND, 8 + vtSize);
        H5.H5Tinsert(tid, "id", 0, HDF5Constants.H5T_NATIVE_INT);
        H5.H5Tinsert(tid, "nested", 8, vt);
        try {
            Object[] buf = {list(7, list(list(1, 2))), list(8, list(list(3, 4), list(5, 6)))};
            writeDataset(fid, "compound_of_vlen_compound", tid, 2, buf, true);
        }
        finally {
            H5.H5Tclose(tid);
            H5.H5Tclose(vt);
            H5.H5Tclose(inner);
        }
    }

    // ---- helpers --------------------------------------------------------------

    private static Dataset open(String name) throws Exception
    {
        Dataset dataset = (Dataset)testFile.get("/" + name);
        assertNotNull(dataset, "Dataset not found: " + name);
        dataset.init();
        return dataset;
    }

    /** Renders a read buffer to a stable string. */
    private static String render(Object data)
    {
        if (data == null)
            return "null";
        if (data instanceof String str)
            return str;
        if (data instanceof List<?> list) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0)
                    sb.append(", ");
                sb.append(render(list.get(i)));
            }
            return sb.append("]").toString();
        }
        if (data.getClass().isArray()) {
            int n = java.lang.reflect.Array.getLength(data);
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < n; i++) {
                if (i > 0)
                    sb.append(", ");
                sb.append(render(java.lang.reflect.Array.get(data, i)));
            }
            return sb.append("]").toString();
        }
        return String.valueOf(data);
    }

    // ---- tests ----------------------------------------------------------------

    @Test
    @DisplayName("VLEN of compound")
    public void testVlenOfCompound() throws Exception
    {
        assertEquals("[[[10, 11], [20, 21]], [[30, 31]]]", render(open("vlen_of_compound").getData()));
    }

    @Test
    @DisplayName("VLEN of variable-length string")
    public void testVlenOfVarStr() throws Exception
    {
        assertEquals("[[a0, a1], [b0]]", render(open("vlen_of_varstr").getData()));
    }

    @Test
    @DisplayName("Array of VLEN of int")
    public void testArrayOfVlenInt() throws Exception
    {
        assertEquals("[[[1, 2], [3]], [[4], [5, 6]]]", render(open("array_of_vlen_int").getData()));
    }

    @Test
    @DisplayName("Array of array of variable-length string")
    public void testArrayOfArrayVarStr() throws Exception
    {
        assertEquals("[[[n000, n001, n002], [n010, n011, n012]], "
                         + "[[n100, n101, n102], [n110, n111, n112]]]",
                     render(open("array_of_array_varstr").getData()));
    }

    @Test
    @DisplayName("Array of array of int")
    public void testArrayOfArrayInt() throws Exception
    {
        Object data = open("array_of_array_int").getData();
        assertInstanceOf(int[].class, data, "No variable-length data, so the layout is flat");
        assertEquals("[0, 1, 2, 10, 11, 12, 100, 101, 102, 110, 111, 112]", render(data));
    }

    @Test
    @DisplayName("Array of compound with a variable-length string member")
    public void testArrayOfCompoundVarStr() throws Exception
    {
        // An array of compound is presented per member, not per element.
        assertEquals("[[0, 1, 2, 3], [x0, x1, y0, y1]]",
                     render(open("array_of_compound_varstr").getData()));
    }

    @Test
    @DisplayName("Compound with a VLEN of compound member")
    public void testCompoundOfVlenCompound() throws Exception
    {
        String rendered = render(open("compound_of_vlen_compound").getData());
        assertTrue(rendered.contains("[1, 2]"), "Expected first record's nested element: " + rendered);
        assertTrue(rendered.contains("[3, 4]"), "Expected second record's first element: " + rendered);
        assertTrue(rendered.contains("[5, 6]"), "Expected second record's second element: " + rendered);
    }
}
