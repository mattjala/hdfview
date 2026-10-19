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

package hdf.object.h5;

import java.util.List;

import hdf.hdf5lib.H5;
import hdf.hdf5lib.HDF5Constants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Marks the values of a compound or array record that the HDF5 library returned the wrong
 * number of.
 *
 * Since 1.14, when a compound holds a variable-length string member whose stored value is
 * NULL, the HDF5 library leaves that member out of the list it returns rather than recording
 * it as null. The list comes back an entry short and every member after the missing one sits
 * a place too early.
 *
 * The values cannot be recovered on HDFView's side. A compound holding two or more
 * variable-length string members gives no way to tell which one was dropped, so every value
 * of an affected record is replaced with an error marker to prevent showing potentially
 * incorrect data. Refilling the record to the length the datatype asks for also puts right
 * the member count that the table uses for indexing.
 *
 * An array loses a NULL element the same way, but its elements carry no names to attach to
 * the wrong value and a short array throws nothing, so the elements that did come back are
 * left to display.
 *
 * See HDFGroup/hdfview#503.
 */
class H5CompoundMemberCheck {

    private static final Logger log = LoggerFactory.getLogger(H5CompoundMemberCheck.class);

    private static final String ERROR_MARKER = "*UNSUPPORTED*";

    private H5CompoundMemberCheck() {}

    /**
     * Mark every record of a buffer returned by H5DreadVL whose value count disagrees with
     * the datatype it was read with.
     *
     * @param tid
     *            the datatype the read was made with
     * @param buf
     *            one entry per selected point
     * @param objName
     *            the dataset or attribute name, for the log
     */
    static void markShortRecords(long tid, Object[] buf, String objName)
    {
        if (buf == null)
            return;

        int marked = 0;

        try {
            /*
             * A member is only ever dropped for being a NULL variable-length string, so a
             * datatype without one cannot have lost anything.
             */
            if (!holdsVarStr(tid))
                return;

            for (Object point : buf)
                marked += walk(tid, point);
        }
        catch (Exception ex) {
            log.debug("markShortRecords(): giving up on {}", objName, ex);
        }

        if (marked > 0)
            log.warn("{} holds {} record(s) the HDF5 library returned the wrong number of values "
                         + "for. Potentially erroneous data was replaced with {}.",
                     objName, marked, ERROR_MARKER);
    }

    /* Returns how many records were marked at or below data. */
    @SuppressWarnings("unchecked")
    private static int walk(long tid, Object data) throws Exception
    {
        if (!(data instanceof List))
            return 0;

        List<Object> values = (List<Object>)data;
        int typeClass       = H5.H5Tget_class(tid);
        int marked          = 0;

        if (typeClass == HDF5Constants.H5T_COMPOUND) {
            int nmembers = H5.H5Tget_nmembers(tid);

            if (values.size() != nmembers) {
                log.debug("walk(): a compound of {} members came back with {} values", nmembers,
                          values.size());
                fill(values, nmembers);
                return 1;
            }

            for (int i = 0; i < nmembers; i++) {
                long memberTid = H5.H5Tget_member_type(tid, i);
                try {
                    marked += walk(memberTid, values.get(i));
                }
                finally {
                    H5.H5Tclose(memberTid);
                }
            }
        }
        else if (typeClass == HDF5Constants.H5T_ARRAY || typeClass == HDF5Constants.H5T_VLEN) {
            /* Neither one names its slots, so a dropped element misplaces nothing. */
            marked += walkElements(tid, values);
        }

        return marked;
    }

    /* Walk each element of a sequence or array against the base type. */
    private static int walkElements(long tid, List<Object> values) throws Exception
    {
        long baseTid = H5.H5Tget_super(tid);
        try {
            int marked = 0;
            for (Object value : values)
                marked += walk(baseTid, value);
            return marked;
        }
        finally {
            H5.H5Tclose(baseTid);
        }
    }

    /* True when tid is, or holds at any depth, a variable-length string. */
    private static boolean holdsVarStr(long tid) throws Exception
    {
        int typeClass = H5.H5Tget_class(tid);

        if (typeClass == HDF5Constants.H5T_STRING)
            return H5.H5Tis_variable_str(tid);

        if (typeClass == HDF5Constants.H5T_COMPOUND) {
            int nmembers = H5.H5Tget_nmembers(tid);

            for (int i = 0; i < nmembers; i++) {
                long memberTid = H5.H5Tget_member_type(tid, i);
                try {
                    if (holdsVarStr(memberTid))
                        return true;
                }
                finally {
                    H5.H5Tclose(memberTid);
                }
            }
            return false;
        }

        if (typeClass == HDF5Constants.H5T_ARRAY || typeClass == HDF5Constants.H5T_VLEN) {
            long baseTid = H5.H5Tget_super(tid);
            try {
                return holdsVarStr(baseTid);
            }
            finally {
                H5.H5Tclose(baseTid);
            }
        }

        return false;
    }

    /* Replace every value of one record, at the length the datatype asks for. */
    private static void fill(List<Object> values, int nvalues)
    {
        values.clear();
        for (int i = 0; i < nvalues; i++)
            values.add(ERROR_MARKER);
    }
}
