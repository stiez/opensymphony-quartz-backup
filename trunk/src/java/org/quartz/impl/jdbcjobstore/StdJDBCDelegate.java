/* 
 * Copyright 2004-2005 OpenSymphony 
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not 
 * use this file except in compliance with the License. You may obtain a copy 
 * of the License at 
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0 
 *   
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT 
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the 
 * License for the specific language governing permissions and limitations 
 * under the License.
 * 
 */

/*
 * Previously Copyright (c) 2001-2004 James House
 */
package org.quartz.impl.jdbcjobstore;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Iterator;
import java.util.HashMap;
import java.util.Set;
import java.util.Properties;
import java.util.TimeZone;

import org.apache.commons.logging.Log;
import org.quartz.Calendar;
import org.quartz.CronTrigger;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.quartz.spi.ClassLoadHelper;
import org.quartz.utils.Key;
import org.quartz.utils.TriggerStatus;

/**
 * <p>
 * This is meant to be an abstract base class for most, if not all, <code>{@link org.quartz.impl.jdbcjobstore.DriverDelegate}</code>
 * implementations. Subclasses should override only those methods that need
 * special handling for the DBMS driver in question.
 * </p>
 * 
 * @author <a href="mailto:jeff@binaryfeed.org">Jeffrey Wescott</a>
 * @author James House
 */
public class StdJDBCDelegate implements DriverDelegate, StdJDBCConstants {

    /*
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     * 
     * Data members.
     * 
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     */

    protected Log logger = null;

    protected String tablePrefix = DEFAULT_TABLE_PREFIX;

    protected String instanceId;

    protected boolean useProperties;

    /*
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     * 
     * Constructors.
     * 
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     */

    /**
     * <p>
     * Create new StdJDBCDelegate instance.
     * </p>
     * 
     * @param logger
     *          the logger to use during execution
     * @param tablePrefix
     *          the prefix of all table names
     */
    public StdJDBCDelegate(Log logger, String tablePrefix, String instanceId) {
        this.logger = logger;
        this.tablePrefix = tablePrefix;
        this.instanceId = instanceId;
    }

    /**
     * <p>
     * Create new StdJDBCDelegate instance.
     * </p>
     * 
     * @param logger
     *          the logger to use during execution
     * @param tablePrefix
     *          the prefix of all table names
     */
    public StdJDBCDelegate(Log logger, String tablePrefix, String instanceId,
            Boolean useProperties) {
        this.logger = logger;
        this.tablePrefix = tablePrefix;
        this.instanceId = instanceId;
        this.useProperties = useProperties.booleanValue();
    }

    /*
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     * 
     * Interface.
     * 
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     */

    protected boolean canUseProperties() {
        return useProperties;
    }

    //---------------------------------------------------------------------------
    // startup / recovery
    //---------------------------------------------------------------------------

    /**
     * <p>
     * Insert the job detail record.
     * </p>
     * 
     * @param conn
     *          the DB Connection
     * @param newState
     *          the new state for the triggers
     * @param oldState1
     *          the first old state to update
     * @param oldState2
     *          the second old state to update
     * @return number of rows updated
     */
    public int updateTriggerStatesFromOtherStates(Connection conn,
            String newState, String oldState1, String oldState2)
            throws SQLException {
        PreparedStatement ps = null;

        try {
            ps = conn
                    .prepareStatement(rtp(UPDATE_TRIGGER_STATES_FROM_OTHER_STATES));
            ps.setString(1, newState);
            ps.setString(2, oldState1);
            ps.setString(3, oldState2);
            return ps.executeUpdate();
        } finally {
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    /**
     * <p>
     * Get the names of all of the triggers that have misfired.
     * </p>
     * 
     * @param conn
     *          the DB Connection
     * @return an array of <code>{@link
     * org.quartz.utils.Key}</code> objects
     */
    public Key[] selectMisfiredTriggers(Connection conn, long ts)
            throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            ps = conn.prepareStatement(rtp(SELECT_MISFIRED_TRIGGERS));
            ps.setBigDecimal(1, new BigDecimal(String.valueOf(ts)));
            rs = ps.executeQuery();

            ArrayList list = new ArrayList();
            while (rs.next()) {
                String triggerName = rs.getString(COL_TRIGGER_NAME);
                String groupName = rs.getString(COL_TRIGGER_GROUP);
                list.add(new Key(triggerName, groupName));
            }
            Object[] oArr = list.toArray();
            Key[] kArr = new Key[oArr.length];
            System.arraycopy(oArr, 0, kArr, 0, oArr.length);
            return kArr;
        } finally {
            if (null != rs) {
                try {
                    rs.close();
                } catch (SQLException ignore) {
                }
            }
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    /**
     * <p>
     * Select all of the triggers in a given state.
     * </p>
     * 
     * @param conn
     *          the DB Connection
     * @param state
     *          the state the triggers must be in
     * @return an array of trigger <code>Key</code> s
     */
    public Key[] selectTriggersInState(Connection conn, String state)
            throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            ps = conn.prepareStatement(rtp(SELECT_TRIGGERS_IN_STATE));
            ps.setString(1, state);
            rs = ps.executeQuery();

            ArrayList list = new ArrayList();
            while (rs.next()) {
                list.add(new Key(rs.getString(1), rs.getString(2)));
            }

            Key[] sArr = (Key[]) list.toArray(new Key[list.size()]);
            return sArr;
        } finally {
            if (null != rs) {
                try {
                    rs.close();
                } catch (SQLException ignore) {
                }
            }
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    public Key[] selectMisfiredTriggersInState(Connection conn, String state,
            long ts) throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            ps = conn.prepareStatement(rtp(SELECT_MISFIRED_TRIGGERS_IN_STATE));
            ps.setBigDecimal(1, new BigDecimal(String.valueOf(ts)));
            ps.setString(2, state);
            rs = ps.executeQuery();

            ArrayList list = new ArrayList();
            while (rs.next()) {
                String triggerName = rs.getString(COL_TRIGGER_NAME);
                String groupName = rs.getString(COL_TRIGGER_GROUP);
                list.add(new Key(triggerName, groupName));
            }
            Object[] oArr = list.toArray();
            Key[] kArr = new Key[oArr.length];
            System.arraycopy(oArr, 0, kArr, 0, oArr.length);
            return kArr;
        } finally {
            if (null != rs) {
                try {
                    rs.close();
                } catch (SQLException ignore) {
                }
            }
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    /**
     * <p>
     * Get the names of all of the triggers in the given group and state that
     * have misfired.
     * </p>
     * 
     * @param conn
     *          the DB Connection
     * @return an array of <code>{@link
     * org.quartz.utils.Key}</code> objects
     */
    public Key[] selectMisfiredTriggersInGroupInState(Connection conn,
            String groupName, String state, long ts) throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            ps = conn
                    .prepareStatement(rtp(SELECT_MISFIRED_TRIGGERS_IN_GROUP_IN_STATE));
            ps.setBigDecimal(1, new BigDecimal(String.valueOf(ts)));
            ps.setString(2, groupName);
            ps.setString(3, state);
            rs = ps.executeQuery();

            ArrayList list = new ArrayList();
            while (rs.next()) {
                String triggerName = rs.getString(COL_TRIGGER_NAME);
                list.add(new Key(triggerName, groupName));
            }
            Object[] oArr = list.toArray();
            Key[] kArr = new Key[oArr.length];
            System.arraycopy(oArr, 0, kArr, 0, oArr.length);
            return kArr;
        } finally {
            if (null != rs) {
                try {
                    rs.close();
                } catch (SQLException ignore) {
                }
            }
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    /**
     * <p>
     * Select all of the triggers for jobs that are requesting recovery. The
     * returned trigger objects will have unique "recoverXXX" trigger names and
     * will be in the <code>{@link
     * org.quartz.Scheduler}.DEFAULT_RECOVERY_GROUP</code>
     * trigger group.
     * </p>
     * 
     * <p>
     * In order to preserve the ordering of the triggers, the fire time will be
     * set from the <code>COL_FIRED_TIME</code> column in the <code>TABLE_FIRED_TRIGGERS</code>
     * table. The caller is responsible for calling <code>computeFirstFireTime</code>
     * on each returned trigger. It is also up to the caller to insert the
     * returned triggers to ensure that they are fired.
     * </p>
     * 
     * @param conn
     *          the DB Connection
     * @return an array of <code>{@link org.quartz.Trigger}</code> objects
     */
    public Trigger[] selectTriggersForRecoveringJobs(Connection conn)
            throws SQLException, IOException, ClassNotFoundException {
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            ps = conn
                    .prepareStatement(rtp(SELECT_INSTANCES_RECOVERABLE_FIRED_TRIGGERS));
            ps.setString(1, instanceId);
            ps.setBoolean(2, true);
            rs = ps.executeQuery();

            long dumId = System.currentTimeMillis();
            ArrayList list = new ArrayList();
            while (rs.next()) {
                String jobName = rs.getString(COL_JOB_NAME);
                String jobGroup = rs.getString(COL_JOB_GROUP);
                String trigName = rs.getString(COL_TRIGGER_NAME);
                String trigGroup = rs.getString(COL_TRIGGER_GROUP);
                long firedTime = rs.getLong(COL_FIRED_TIME);
                SimpleTrigger rcvryTrig = new SimpleTrigger("recover_"
                        + instanceId + "_" + String.valueOf(dumId++),
                        Scheduler.DEFAULT_RECOVERY_GROUP, new Date(firedTime));
                rcvryTrig.setJobName(jobName);
                rcvryTrig.setJobGroup(jobGroup);
                rcvryTrig
                        .setMisfireInstruction(SimpleTrigger.MISFIRE_INSTRUCTION_FIRE_NOW);

                JobDataMap jd = selectTriggerJobDataMap(conn, trigName, trigGroup);
                jd.put("QRTZ_FAILED_JOB_ORIG_TRIGGER_NAME", trigName);
                jd.put("QRTZ_FAILED_JOB_ORIG_TRIGGER_GROUP", trigGroup);
                jd.put("QRTZ_FAILED_JOB_ORIG_TRIGGER_FIRETIME_IN_MILLISECONDS", firedTime);
                rcvryTrig.setJobDataMap(jd);
                
                list.add(rcvryTrig);
            }
            Object[] oArr = list.toArray();
            Trigger[] tArr = new Trigger[oArr.length];
            System.arraycopy(oArr, 0, tArr, 0, oArr.length);
            return tArr;
        } finally {
            if (null != rs) {
                try {
                    rs.close();
                } catch (SQLException ignore) {
                }
            }
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    /**
     * <p>
     * Delete all fired triggers.
     * </p>
     * 
     * @param conn
     *          the DB Connection
     * @return the number of rows deleted
     */
    public int deleteFiredTriggers(Connection conn) throws SQLException {
        PreparedStatement ps = null;

        try {
            ps = conn.prepareStatement(rtp(DELETE_FIRED_TRIGGERS));

            return ps.executeUpdate();
        } finally {
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    public int deleteFiredTriggers(Connection conn, String instanceId)
            throws SQLException {
        PreparedStatement ps = null;

        try {
            ps = conn.prepareStatement(rtp(DELETE_INSTANCES_FIRED_TRIGGERS));
            ps.setString(1, instanceId);

            return ps.executeUpdate();
        } finally {
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    //---------------------------------------------------------------------------
    // jobs
    //---------------------------------------------------------------------------

    /**
     * <p>
     * Insert the job detail record.
     * </p>
     * 
     * @param conn
     *          the DB Connection
     * @param job
     *          the job to insert
     * @return number of rows inserted
     * @throws IOException
     *           if there were problems serializing the JobDataMap
     */
    public int insertJobDetail(Connection conn, JobDetail job)
            throws IOException, SQLException {
        ByteArrayOutputStream baos = serializeJobData(job.getJobDataMap());

        PreparedStatement ps = null;

        int insertResult = 0;

        try {
            ps = conn.prepareStatement(rtp(INSERT_JOB_DETAIL));
            ps.setString(1, job.getName());
            ps.setString(2, job.getGroup());
            ps.setString(3, job.getDescription());
            ps.setString(4, job.getJobClass().getName());
            ps.setBoolean(5, job.isDurable());
            ps.setBoolean(6, job.isVolatile());
            ps.setBoolean(7, job.isStateful());
            ps.setBoolean(8, job.requestsRecovery());
            ps.setBytes(9, baos.toByteArray());

            insertResult = ps.executeUpdate();
        } finally {
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }

        if (insertResult > 0) {
            String[] jobListeners = job.getJobListenerNames();
            for (int i = 0; jobListeners != null && i < jobListeners.length; i++)
                insertJobListener(conn, job, jobListeners[i]);
        }

        return insertResult;
    }

    /**
     * <p>
     * Update the job detail record.
     * </p>
     * 
     * @param conn
     *          the DB Connection
     * @param job
     *          the job to update
     * @return number of rows updated
     * @throws IOException
     *           if there were problems serializing the JobDataMap
     */
    public int updateJobDetail(Connection conn, JobDetail job)
            throws IOException, SQLException {
        ByteArrayOutputStream baos = serializeJobData(job.getJobDataMap());

        PreparedStatement ps = null;

        int insertResult = 0;

        try {
            ps = conn.prepareStatement(rtp(UPDATE_JOB_DETAIL));
            ps.setString(1, job.getDescription());
            ps.setString(2, job.getJobClass().getName());
            ps.setBoolean(3, job.isDurable());
            ps.setBoolean(4, job.isVolatile());
            ps.setBoolean(5, job.isStateful());
            ps.setBoolean(6, job.requestsRecovery());
            ps.setBytes(7, baos.toByteArray());
            ps.setString(8, job.getName());
            ps.setString(9, job.getGroup());

            insertResult = ps.executeUpdate();
        } finally {
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }

        if (insertResult > 0) {
            deleteJobListeners(conn, job.getName(), job.getGroup());

            String[] jobListeners = job.getJobListenerNames();
            for (int i = 0; jobListeners != null && i < jobListeners.length; i++)
                insertJobListener(conn, job, jobListeners[i]);
        }

        return insertResult;
    }

    /**
     * <p>
     * Get the names of all of the triggers associated with the given job.
     * </p>
     * 
     * @param conn
     *          the DB Connection
     * @param jobName
     *          the name of the job
     * @param groupName
     *          the group containing the job
     * @return an array of <code>{@link
     * org.quartz.utils.Key}</code> objects
     */
    public Key[] selectTriggerNamesForJob(Connection conn, String jobName,
            String groupName) throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            ps = conn.prepareStatement(rtp(SELECT_TRIGGERS_FOR_JOB));
            ps.setString(1, jobName);
            ps.setString(2, groupName);
            rs = ps.executeQuery();

            ArrayList list = new ArrayList(10);
            while (rs.next()) {
                String trigName = rs.getString(COL_TRIGGER_NAME);
                String trigGroup = rs.getString(COL_TRIGGER_GROUP);
                list.add(new Key(trigName, trigGroup));
            }
            Object[] oArr = list.toArray();
            Key[] kArr = new Key[oArr.length];
            System.arraycopy(oArr, 0, kArr, 0, oArr.length);
            return kArr;
        } finally {
            if (null != rs) {
                try {
                    rs.close();
                } catch (SQLException ignore) {
                }
            }
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    /**
     * <p>
     * Delete all job listeners for the given job.
     * </p>
     * 
     * @param conn
     *          the DB Connection
     * @param jobName
     *          the name of the job
     * @param groupName
     *          the group containing the job
     * @return the number of rows deleted
     */
    public int deleteJobListeners(Connection conn, String jobName,
            String groupName) throws SQLException {
        PreparedStatement ps = null;

        try {
            ps = conn.prepareStatement(rtp(DELETE_JOB_LISTENERS));
            ps.setString(1, jobName);
            ps.setString(2, groupName);
            return ps.executeUpdate();
        } finally {
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    /**
     * <p>
     * Delete the job detail record for the given job.
     * </p>
     * 
     * @param conn
     *          the DB Connection
     * @param jobName
     *          the name of the job
     * @param groupName
     *          the group containing the job
     * @return the number of rows deleted
     */
    public int deleteJobDetail(Connection conn, String jobName, String groupName)
            throws SQLException {
        PreparedStatement ps = null;

        try {
            logger.debug("Deleting job: " + groupName + "." + jobName);
            ps = conn.prepareStatement(rtp(DELETE_JOB_DETAIL));
            ps.setString(1, jobName);
            ps.setString(2, groupName);
            return ps.executeUpdate();
        } finally {
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    /**
     * <p>
     * Check whether or not the given job is stateful.
     * </p>
     * 
     * @param conn
     *          the DB Connection
     * @param jobName
     *          the name of the job
     * @param groupName
     *          the group containing the job
     * @return true if the job exists and is stateful, false otherwise
     */
    public boolean isJobStateful(Connection conn, String jobName,
            String groupName) throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            ps = conn.prepareStatement(rtp(SELECT_JOB_STATEFUL));
            ps.setString(1, jobName);
            ps.setString(2, groupName);
            rs = ps.executeQuery();
            if (!rs.next()) { return false; }
            return rs.getBoolean(COL_IS_STATEFUL);
        } finally {
            if (null != rs) {
                try {
                    rs.close();
                } catch (SQLException ignore) {
                }
            }
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    /**
     * <p>
     * Check whether or not the given job exists.
     * </p>
     * 
     * @param conn
     *          the DB Connection
     * @param jobName
     *          the name of the job
     * @param groupName
     *          the group containing the job
     * @return true if the job exists, false otherwise
     */
    public boolean jobExists(Connection conn, String jobName, String groupName)
            throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            ps = conn.prepareStatement(rtp(SELECT_JOB_EXISTENCE));
            ps.setString(1, jobName);
            ps.setString(2, groupName);
            rs = ps.executeQuery();
            if (rs.next()) {
                return true;
            } else {
                return false;
            }
        } finally {
            if (null != rs) {
                try {
                    rs.close();
                } catch (SQLException ignore) {
                }
            }
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }

    }

    /**
     * <p>
     * Update the job data map for the given job.
     * </p>
     * 
     * @param conn
     *          the DB Connection
     * @param job
     *          the job to update
     * @return the number of rows updated
     */
    public int updateJobData(Connection conn, JobDetail job)
            throws IOException, SQLException {
        ByteArrayOutputStream baos = serializeJobData(job.getJobDataMap());

        PreparedStatement ps = null;

        try {
            ps = conn.prepareStatement(rtp(UPDATE_JOB_DATA));
            ps.setBytes(1, baos.toByteArray());
            ps.setString(2, job.getName());
            ps.setString(3, job.getGroup());

            return ps.executeUpdate();
        } finally {
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    /**
     * <p>
     * Associate a listener with a job.
     * </p>
     * 
     * @param conn
     *          the DB Connection
     * @param job
     *          the job to associate with the listener
     * @param listener
     *          the listener to insert
     * @return the number of rows inserted
     */
    public int insertJobListener(Connection conn, JobDetail job, String listener)
            throws SQLException {
        PreparedStatement ps = null;

        try {
            ps = conn.prepareStatement(rtp(INSERT_JOB_LISTENER));
            ps.setString(1, job.getName());
            ps.setString(2, job.getGroup());
            ps.setString(3, listener);

            return ps.executeUpdate();
        } finally {
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    /**
     * <p>
     * Get all of the listeners for a given job.
     * </p>
     * 
     * @param conn
     *          the DB Connection
     * @param jobName
     *          the job name whose listeners are wanted
     * @param groupName
     *          the group containing the job
     * @return array of <code>String</code> listener names
     */
    public String[] selectJobListeners(Connection conn, String jobName,
            String groupName) throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            ArrayList list = new ArrayList();
            ps = conn.prepareStatement(rtp(SELECT_JOB_LISTENERS));
            ps.setString(1, jobName);
            ps.setString(2, groupName);
            rs = ps.executeQuery();

            while (rs.next()) {
                list.add(rs.getString(1));
            }

            Object[] oArr = list.toArray();
            String[] sArr = new String[oArr.length];
            System.arraycopy(oArr, 0, sArr, 0, oArr.length);
            return sArr;
        } finally {
            if (null != rs) {
                try {
                    rs.close();
                } catch (SQLException ignore) {
                }
            }
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    /**
     * <p>
     * Select the JobDetail object for a given job name / group name.
     * </p>
     * 
     * @param conn
     *          the DB Connection
     * @param jobName
     *          the job name whose listeners are wanted
     * @param groupName
     *          the group containing the job
     * @return the populated JobDetail object
     * @throws ClassNotFoundException
     *           if a class found during deserialization cannot be found or if
     *           the job class could not be found
     * @throws IOException
     *           if deserialization causes an error
     */
    public JobDetail selectJobDetail(Connection conn, String jobName,
            String groupName, ClassLoadHelper loadHelper)
            throws ClassNotFoundException, IOException, SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            ps = conn.prepareStatement(rtp(SELECT_JOB_DETAIL));
            ps.setString(1, jobName);
            ps.setString(2, groupName);
            rs = ps.executeQuery();

            JobDetail job = null;

            if (rs.next()) {
                job = new JobDetail();

                job.setName(rs.getString(COL_JOB_NAME));
                job.setGroup(rs.getString(COL_JOB_GROUP));
                job.setDescription(rs.getString(COL_DESCRIPTION));
                job.setJobClass(loadHelper.loadClass(rs
                        .getString(COL_JOB_CLASS)));
                job.setDurability(rs.getBoolean(COL_IS_DURABLE));
                job.setVolatility(rs.getBoolean(COL_IS_VOLATILE));
                job.setRequestsRecovery(rs.getBoolean(COL_REQUESTS_RECOVERY));

                Map map = null;
                if (canUseProperties()) map = getMapFromProperties(rs);
                else
                    map = (Map) getObjectFromBlob(rs, COL_JOB_DATAMAP);

                if (null != map) {
                    job.setJobDataMap(new JobDataMap(map));
                }
            }

            return job;
        } finally {
            if (null != rs) {
                try {
                    rs.close();
                } catch (SQLException ignore) {
                }
            }
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    /**
     * build Map from java.util.Properties encoding.
     */
    private Map getMapFromProperties(ResultSet rs)
            throws ClassNotFoundException, IOException, SQLException {
        Map map;
        InputStream is = (InputStream) getJobDetailFromBlob(rs, COL_JOB_DATAMAP);
        if(is == null)
            return null;
        Properties properties = new Properties();
        if (is != null) properties.load(is);
        map = convertFromProperty(properties);
        return map;
    }

    /**
     * <p>
     * Select the total number of jobs stored.
     * </p>
     * 
     * @param conn
     *          the DB Connection
     * @return the total number of jobs stored
     */
    public int selectNumJobs(Connection conn) throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            int count = 0;
            ps = conn.prepareStatement(rtp(SELECT_NUM_JOBS));
            rs = ps.executeQuery();

            if (rs.next()) {
                count = rs.getInt(1);
            }

            return count;
        } finally {
            if (null != rs) {
                try {
                    rs.close();
                } catch (SQLException ignore) {
                }
            }
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    /**
     * <p>
     * Select all of the job group names that are stored.
     * </p>
     * 
     * @param conn
     *          the DB Connection
     * @return an array of <code>String</code> group names
     */
    public String[] selectJobGroups(Connection conn) throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            ps = conn.prepareStatement(rtp(SELECT_JOB_GROUPS));
            rs = ps.executeQuery();

            ArrayList list = new ArrayList();
            while (rs.next()) {
                list.add(rs.getString(1));
            }

            Object[] oArr = list.toArray();
            String[] sArr = new String[oArr.length];
            System.arraycopy(oArr, 0, sArr, 0, oArr.length);
            return sArr;
        } finally {
            if (null != rs) {
                try {
                    rs.close();
                } catch (SQLException ignore) {
                }
            }
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    /**
     * <p>
     * Select all of the jobs contained in a given group.
     * </p>
     * 
     * @param conn
     *          the DB Connection
     * @param groupName
     *          the group containing the jobs
     * @return an array of <code>String</code> job names
     */
    public String[] selectJobsInGroup(Connection conn, String groupName)
            throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            ps = conn.prepareStatement(rtp(SELECT_JOBS_IN_GROUP));
            ps.setString(1, groupName);
            rs = ps.executeQuery();

            ArrayList list = new ArrayList();
            while (rs.next()) {
                list.add(rs.getString(1));
            }

            Object[] oArr = list.toArray();
            String[] sArr = new String[oArr.length];
            System.arraycopy(oArr, 0, sArr, 0, oArr.length);
            return sArr;
        } finally {
            if (null != rs) {
                try {
                    rs.close();
                } catch (SQLException ignore) {
                }
            }
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    //---------------------------------------------------------------------------
    // triggers
    //---------------------------------------------------------------------------

    /**
     * <p>
     * Insert the base trigger data.
     * </p>
     * 
     * @param conn
     *          the DB Connection
     * @param trigger
     *          the trigger to insert
     * @param state
     *          the state that the trigger should be stored in
     * @return the number of rows inserted
     */
    public int insertTrigger(Connection conn, Trigger trigger, String state,
            JobDetail jobDetail) throws SQLException, IOException {

        ByteArrayOutputStream baos = null;
        if(trigger.getJobDataMap().size() > 0)
            baos = serializeJobData(trigger.getJobDataMap());
        
        PreparedStatement ps = null;

        int insertResult = 0;

        try {
            ps = conn.prepareStatement(rtp(INSERT_TRIGGER));
            ps.setString(1, trigger.getName());
            ps.setString(2, trigger.getGroup());
            ps.setString(3, trigger.getJobName());
            ps.setString(4, trigger.getJobGroup());
            ps.setBoolean(5, trigger.isVolatile());
            ps.setString(6, trigger.getDescription());
            ps.setBigDecimal(7, new BigDecimal(String.valueOf(trigger
                    .getNextFireTime().getTime())));
            long prevFireTime = -1;
            if (trigger.getPreviousFireTime() != null) {
                prevFireTime = trigger.getPreviousFireTime().getTime();
            }
            ps.setBigDecimal(8, new BigDecimal(String.valueOf(prevFireTime)));
            ps.setString(9, state);
            if (trigger instanceof SimpleTrigger) {
                ps.setString(10, TTYPE_SIMPLE);
            } else if (trigger instanceof CronTrigger) {
                ps.setString(10, TTYPE_CRON);
            } else { // (trigger instanceof BlobTrigger)
                ps.setString(10, TTYPE_BLOB);
            }
            ps.setBigDecimal(11, new BigDecimal(String.valueOf(trigger
                    .getStartTime().getTime())));
            long endTime = 0;
            if (trigger.getEndTime() != null) {
                endTime = trigger.getEndTime().getTime();
            }
            ps.setBigDecimal(12, new BigDecimal(String.valueOf(endTime)));
            ps.setString(13, trigger.getCalendarName());
            ps.setInt(14, trigger.getMisfireInstruction());
            if(baos != null)
                ps.setBytes(15, baos.toByteArray());
            else
                ps.setBytes(15, null);
            
            insertResult = ps.executeUpdate();
        } finally {
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }

        if (insertResult > 0) {
            String[] trigListeners = trigger.getTriggerListenerNames();
            for (int i = 0; trigListeners != null && i < trigListeners.length; i++)
                insertTriggerListener(conn, trigger, trigListeners[i]);
        }

        return insertResult;
    }

    /**
     * <p>
     * Insert the simple trigger data.
     * </p>
     * 
     * @param conn
     *          the DB Connection
     * @param trigger
     *          the trigger to insert
     * @return the number of rows inserted
     */
    public int insertSimpleTrigger(Connection conn, SimpleTrigger trigger)
            throws SQLException {
        PreparedStatement ps = null;

        try {
            ps = conn.prepareStatement(rtp(INSERT_SIMPLE_TRIGGER));
            ps.setString(1, trigger.getName());
            ps.setString(2, trigger.getGroup());
            ps.setInt(3, trigger.getRepeatCount());
            ps.setBigDecimal(4, new BigDecimal(String.valueOf(trigger
                    .getRepeatInterval())));
            ps.setInt(5, trigger.getTimesTriggered());

            return ps.executeUpdate();
        } finally {
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    /**
     * <p>
     * Insert the cron trigger data.
     * </p>
     * 
     * @param conn
     *          the DB Connection
     * @param trigger
     *          the trigger to insert
     * @return the number of rows inserted
     */
    public int insertCronTrigger(Connection conn, CronTrigger trigger)
            throws SQLException {
        PreparedStatement ps = null;

        try {
            ps = conn.prepareStatement(rtp(INSERT_CRON_TRIGGER));
            ps.setString(1, trigger.getName());
            ps.setString(2, trigger.getGroup());
            ps.setString(3, trigger.getCronExpression());
            ps.setString(4, trigger.getTimeZone().getID());

            return ps.executeUpdate();
        } finally {
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    /**
     * <p>
     * Insert the blob trigger data.
     * </p>
     * 
     * @param conn
     *          the DB Connection
     * @param trigger
     *          the trigger to insert
     * @return the number of rows inserted
     */
    public int insertBlobTrigger(Connection conn, Trigger trigger)
            throws SQLException, IOException {
        PreparedStatement ps = null;
        ByteArrayOutputStream os = null;

        try {
            // update the blob
            os = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(os);
            oos.writeObject(trigger);
            oos.close();

            byte[] buf = os.toByteArray();
            ByteArrayInputStream is = new ByteArrayInputStream(buf);

            ps = conn.prepareStatement(rtp(INSERT_BLOB_TRIGGER));
            ps.setString(1, trigger.getName());
            ps.setString(2, trigger.getGroup());
            ps.setBinaryStream(3, is, buf.length);

            return ps.executeUpdate();
        } finally {
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    /**
     * <p>
     * Update the base trigger data.
     * </p>
     * 
     * @param conn
     *          the DB Connection
     * @param trigger
     *          the trigger to insert
     * @param state
     *          the state that the trigger should be stored in
     * @return the number of rows updated
     */
    public int updateTrigger(Connection conn, Trigger trigger, String state,
            JobDetail jobDetail) throws SQLException, IOException {

        // save some clock cycles by unnecessarily writing job data blob ...
        boolean updateJobData = trigger.getJobDataMap().isDirty();
        ByteArrayOutputStream baos = null;
        if(updateJobData && trigger.getJobDataMap().size() > 0)
            baos = serializeJobData(trigger.getJobDataMap());
                
        PreparedStatement ps = null;

        int insertResult = 0;


        try {
            if(updateJobData)
                ps = conn.prepareStatement(rtp(UPDATE_TRIGGER));
            else
                ps = conn.prepareStatement(rtp(UPDATE_TRIGGER_SKIP_DATA));
                
            ps.setString(1, trigger.getJobName());
            ps.setString(2, trigger.getJobGroup());
            ps.setBoolean(3, trigger.isVolatile());
            ps.setString(4, trigger.getDescription());
            long nextFireTime = -1;
            if (trigger.getNextFireTime() != null) {
                nextFireTime = trigger.getNextFireTime().getTime();
            }
            ps.setBigDecimal(5, new BigDecimal(String.valueOf(nextFireTime)));
            long prevFireTime = -1;
            if (trigger.getPreviousFireTime() != null) {
                prevFireTime = trigger.getPreviousFireTime().getTime();
            }
            ps.setBigDecimal(6, new BigDecimal(String.valueOf(prevFireTime)));
            ps.setString(7, state);
            if (trigger instanceof SimpleTrigger) {
                //                updateSimpleTrigger(conn, (SimpleTrigger)trigger);
                ps.setString(8, TTYPE_SIMPLE);
            } else if (trigger instanceof CronTrigger) {
                //                updateCronTrigger(conn, (CronTrigger)trigger);
                ps.setString(8, TTYPE_CRON);
            } else {
                //                updateBlobTrigger(conn, trigger);
                ps.setString(8, TTYPE_BLOB);
            }
            ps.setBigDecimal(9, new BigDecimal(String.valueOf(trigger
                    .getStartTime().getTime())));
            long endTime = 0;
            if (trigger.getEndTime() != null) {
                endTime = trigger.getEndTime().getTime();
            }
            ps.setBigDecimal(10, new BigDecimal(String.valueOf(endTime)));
            ps.setString(11, trigger.getCalendarName());
            ps.setInt(12, trigger.getMisfireInstruction());
            if(updateJobData) {
                ps.setBytes(13, baos.toByteArray());
                
                ps.setString(14, trigger.getName());
                ps.setString(15, trigger.getGroup());
            }
            else {
                ps.setString(13, trigger.getName());
                ps.setString(14, trigger.getGroup());
            }

            insertResult = ps.executeUpdate();
        } finally {
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }

        if (insertResult > 0) {
            deleteTriggerListeners(conn, trigger.getName(), trigger.getGroup());

            String[] trigListeners = trigger.getTriggerListenerNames();
            for (int i = 0; trigListeners != null && i < trigListeners.length; i++)
                insertTriggerListener(conn, trigger, trigListeners[i]);
        }

        return insertResult;
    }

    /**
     * <p>
     * Update the simple trigger data.
     * </p>
     * 
     * @param conn
     *          the DB Connection
     * @param trigger
     *          the trigger to insert
     * @return the number of rows updated
     */
    public int updateSimpleTrigger(Connection conn, SimpleTrigger trigger)
            throws SQLException {
        PreparedStatement ps = null;

        try {
            ps = conn.prepareStatement(rtp(UPDATE_SIMPLE_TRIGGER));

            ps.setInt(1, trigger.getRepeatCount());
            ps.setBigDecimal(2, new BigDecimal(String.valueOf(trigger
                    .getRepeatInterval())));
            ps.setInt(3, trigger.getTimesTriggered());
            ps.setString(4, trigger.getName());
            ps.setString(5, trigger.getGroup());

            return ps.executeUpdate();
        } finally {
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    /**
     * <p>
     * Update the cron trigger data.
     * </p>
     * 
     * @param conn
     *          the DB Connection
     * @param trigger
     *          the trigger to insert
     * @return the number of rows updated
     */
    public int updateCronTrigger(Connection conn, CronTrigger trigger)
            throws SQLException {
        PreparedStatement ps = null;

        try {
            ps = conn.prepareStatement(rtp(UPDATE_CRON_TRIGGER));
            ps.setString(1, trigger.getCronExpression());
            ps.setString(2, trigger.getName());
            ps.setString(3, trigger.getGroup());

            return ps.executeUpdate();
        } finally {
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    /**
     * <p>
     * Update the blob trigger data.
     * </p>
     * 
     * @param conn
     *          the DB Connection
     * @param trigger
     *          the trigger to insert
     * @return the number of rows updated
     */
    public int updateBlobTrigger(Connection conn, Trigger trigger)
            throws SQLException, IOException {
        PreparedStatement ps = null;
        ByteArrayOutputStream os = null;

        try {
            // update the blob
            os = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(os);
            oos.writeObject(trigger);
            oos.close();

            byte[] buf = os.toByteArray();
            ByteArrayInputStream is = new ByteArrayInputStream(buf);

            ps = conn.prepareStatement(rtp(UPDATE_BLOB_TRIGGER));
            ps.setBinaryStream(1, is, buf.length);
            ps.setString(2, trigger.getName());
            ps.setString(3, trigger.getGroup());

            return ps.executeUpdate();
        } finally {
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
            if (os != null) os.close();
        }
    }

    /**
     * <p>
     * Check whether or not a trigger exists.
     * </p>
     * 
     * @param conn
     *          the DB Connection
     * @param triggerName
     *          the name of the trigger
     * @param groupName
     *          the group containing the trigger
     * @return true if the trigger exists, false otherwise
     */
    public boolean triggerExists(Connection conn, String triggerName,
            String groupName) throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            ps = conn.prepareStatement(rtp(SELECT_TRIGGER_EXISTENCE));
            ps.setString(1, triggerName);
            ps.setString(2, groupName);
            rs = ps.executeQuery();

            if (rs.next()) {
                return true;
            } else {
                return false;
            }
        } finally {
            if (null != rs) {
                try {
                    rs.close();
                } catch (SQLException ignore) {
                }
            }
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    /**
     * <p>
     * Update the state for a given trigger.
     * </p>
     * 
     * @param conn
     *          the DB Connection
     * @param triggerName
     *          the name of the trigger
     * @param groupName
     *          the group containing the trigger
     * @param state
     *          the new state for the trigger
     * @return the number of rows updated
     */
    public int updateTriggerState(Connection conn, String triggerName,
            String groupName, String state) throws SQLException {
        PreparedStatement ps = null;

        try {
            ps = conn.prepareStatement(rtp(UPDATE_TRIGGER_STATE));
            ps.setString(1, state);
            ps.setString(2, triggerName);
            ps.setString(3, groupName);

            return ps.executeUpdate();
        } finally {
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    /**
     * <p>
     * Update the given trigger to the given new state, if it is one of the
     * given old states.
     * </p>
     * 
     * @param conn
     *          the DB connection
     * @param triggerName
     *          the name of the trigger
     * @param groupName
     *          the group containing the trigger
     * @param newState
     *          the new state for the trigger
     * @param oldState1
     *          one of the old state the trigger must be in
     * @param oldState2
     *          one of the old state the trigger must be in
     * @param oldState3
     *          one of the old state the trigger must be in
     * @return int the number of rows updated
     * @throws SQLException
     */
    public int updateTriggerStateFromOtherStates(Connection conn,
            String triggerName, String groupName, String newState,
            String oldState1, String oldState2, String oldState3)
            throws SQLException {
        PreparedStatement ps = null;

        try {
            ps = conn.prepareStatement(rtp(UPDATE_TRIGGER_STATE_FROM_STATES));
            ps.setString(1, newState);
            ps.setString(2, triggerName);
            ps.setString(3, groupName);
            ps.setString(4, oldState1);
            ps.setString(5, oldState2);
            ps.setString(6, oldState3);

            return ps.executeUpdate();
        } finally {
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    public int updateTriggerStateFromOtherStatesBeforeTime(Connection conn,
            String newState, String oldState1, String oldState2, long time)
            throws SQLException {
        PreparedStatement ps = null;

        try {
            ps = conn
                    .prepareStatement(rtp(UPDATE_TRIGGER_STATE_FROM_OTHER_STATES_BEFORE_TIME));
            ps.setString(1, newState);
            ps.setString(2, oldState1);
            ps.setString(3, oldState2);
            ps.setLong(4, time);

            return ps.executeUpdate();
        } finally {
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    /**
     * <p>
     * Update all triggers in the given group to the given new state, if they
     * are in one of the given old states.
     * </p>
     * 
     * @param conn
     *          the DB connection
     * @param groupName
     *          the group containing the trigger
     * @param newState
     *          the new state for the trigger
     * @param oldState1
     *          one of the old state the trigger must be in
     * @param oldState2
     *          one of the old state the trigger must be in
     * @param oldState3
     *          one of the old state the trigger must be in
     * @return int the number of rows updated
     * @throws SQLException
     */
    public int updateTriggerGroupStateFromOtherStates(Connection conn,
            String groupName, String newState, String oldState1,
            String oldState2, String oldState3) throws SQLException {
        PreparedStatement ps = null;

        try {
            ps = conn
                    .prepareStatement(rtp(UPDATE_TRIGGER_GROUP_STATE_FROM_STATES));
            ps.setString(1, newState);
            ps.setString(2, groupName);
            ps.setString(3, oldState1);
            ps.setString(4, oldState2);
            ps.setString(5, oldState3);

            return ps.executeUpdate();
        } finally {
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    /**
     * <p>
     * Update the given trigger to the given new state, if it is in the given
     * old state.
     * </p>
     * 
     * @param conn
     *          the DB connection
     * @param triggerName
     *          the name of the trigger
     * @param groupName
     *          the group containing the trigger
     * @param newState
     *          the new state for the trigger
     * @param oldState
     *          the old state the trigger must be in
     * @return int the number of rows updated
     * @throws SQLException
     */
    public int updateTriggerStateFromOtherState(Connection conn,
            String triggerName, String groupName, String newState,
            String oldState) throws SQLException {
        PreparedStatement ps = null;

        try {
            ps = conn.prepareStatement(rtp(UPDATE_TRIGGER_STATE_FROM_STATE));
            ps.setString(1, newState);
            ps.setString(2, triggerName);
            ps.setString(3, groupName);
            ps.setString(4, oldState);

            return ps.executeUpdate();
        } finally {
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    /**
     * <p>
     * Update all of the triggers of the given group to the given new state, if
     * they are in the given old state.
     * </p>
     * 
     * @param conn
     *          the DB connection
     * @param groupName
     *          the group containing the triggers
     * @param newState
     *          the new state for the trigger group
     * @param oldState
     *          the old state the triggers must be in
     * @return int the number of rows updated
     * @throws SQLException
     */
    public int updateTriggerGroupStateFromOtherState(Connection conn,
            String groupName, String newState, String oldState)
            throws SQLException {
        PreparedStatement ps = null;

        try {
            ps = conn
                    .prepareStatement(rtp(UPDATE_TRIGGER_GROUP_STATE_FROM_STATE));
            ps.setString(1, newState);
            ps.setString(2, groupName);
            ps.setString(3, oldState);

            return ps.executeUpdate();
        } finally {
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    /**
     * <p>
     * Update the states of all triggers associated with the given job.
     * </p>
     * 
     * @param conn
     *          the DB Connection
     * @param jobName
     *          the name of the job
     * @param groupName
     *          the group containing the job
     * @param state
     *          the new state for the triggers
     * @return the number of rows updated
     */
    public int updateTriggerStatesForJob(Connection conn, String jobName,
            String groupName, String state) throws SQLException {
        PreparedStatement ps = null;

        try {
            ps = conn.prepareStatement(rtp(UPDATE_JOB_TRIGGER_STATES));
            ps.setString(1, state);
            ps.setString(2, jobName);
            ps.setString(3, groupName);

            return ps.executeUpdate();
        } finally {
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    public int updateTriggerStatesForJobFromOtherState(Connection conn,
            String jobName, String groupName, String state, String oldState)
            throws SQLException {
        PreparedStatement ps = null;

        try {
            ps = conn
                    .prepareStatement(rtp(UPDATE_JOB_TRIGGER_STATES_FROM_OTHER_STATE));
            ps.setString(1, state);
            ps.setString(2, jobName);
            ps.setString(3, groupName);
            ps.setString(4, oldState);

            return ps.executeUpdate();
        } finally {
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    /**
     * <p>
     * Delete all of the listeners associated with a given trigger.
     * </p>
     * 
     * @param conn
     *          the DB Connection
     * @param triggerName
     *          the name of the trigger whose listeners will be deleted
     * @param groupName
     *          the name of the group containing the trigger
     * @return the number of rows deleted
     */
    public int deleteTriggerListeners(Connection conn, String triggerName,
            String groupName) throws SQLException {
        PreparedStatement ps = null;

        try {
            ps = conn.prepareStatement(rtp(DELETE_TRIGGER_LISTENERS));
            ps.setString(1, triggerName);
            ps.setString(2, groupName);
            return ps.executeUpdate();
        } finally {
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    /**
     * <p>
     * Associate a listener with the given trigger.
     * </p>
     * 
     * @param conn
     *          the DB Connection
     * @param trigger
     *          the trigger
     * @param listener
     *          the name of the listener to associate with the trigger
     * @return the number of rows inserted
     */
    public int insertTriggerListener(Connection conn, Trigger trigger,
            String listener) throws SQLException {
        PreparedStatement ps = null;

        try {
            ps = conn.prepareStatement(rtp(INSERT_TRIGGER_LISTENER));
            ps.setString(1, trigger.getName());
            ps.setString(2, trigger.getGroup());
            ps.setString(3, listener);

            return ps.executeUpdate();
        } finally {
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    /**
     * <p>
     * Select the listeners associated with a given trigger.
     * </p>
     * 
     * @param conn
     *          the DB Connection
     * @param triggerName
     *          the name of the trigger
     * @param groupName
     *          the group containing the trigger
     * @return array of <code>String</code> trigger listener names
     */
    public String[] selectTriggerListeners(Connection conn, String triggerName,
            String groupName) throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            ps = conn.prepareStatement(rtp(SELECT_TRIGGER_LISTENERS));
            ps.setString(1, triggerName);
            ps.setString(2, groupName);
            rs = ps.executeQuery();

            ArrayList list = new ArrayList();
            while (rs.next()) {
                list.add(rs.getString(1));
            }
            Object[] oArr = list.toArray();
            String[] sArr = new String[oArr.length];
            System.arraycopy(oArr, 0, sArr, 0, oArr.length);
            return sArr;
        } finally {
            if (null != rs) {
                try {
                    rs.close();
                } catch (SQLException ignore) {
                }
            }
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    /**
     * <p>
     * Delete the simple trigger data for a trigger.
     * </p>
     * 
     * @param conn
     *          the DB Connection
     * @param triggerName
     *          the name of the trigger
     * @param groupName
     *          the group containing the trigger
     * @return the number of rows deleted
     */
    public int deleteSimpleTrigger(Connection conn, String triggerName,
            String groupName) throws SQLException {
        PreparedStatement ps = null;

        try {
            ps = conn.prepareStatement(rtp(DELETE_SIMPLE_TRIGGER));
            ps.setString(1, triggerName);
            ps.setString(2, groupName);

            return ps.executeUpdate();
        } finally {
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    /**
     * <p>
     * Delete the cron trigger data for a trigger.
     * </p>
     * 
     * @param conn
     *          the DB Connection
     * @param triggerName
     *          the name of the trigger
     * @param groupName
     *          the group containing the trigger
     * @return the number of rows deleted
     */
    public int deleteCronTrigger(Connection conn, String triggerName,
            String groupName) throws SQLException {
        PreparedStatement ps = null;

        try {
            ps = conn.prepareStatement(rtp(DELETE_CRON_TRIGGER));
            ps.setString(1, triggerName);
            ps.setString(2, groupName);

            return ps.executeUpdate();
        } finally {
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    /**
     * <p>
     * Delete the cron trigger data for a trigger.
     * </p>
     * 
     * @param conn
     *          the DB Connection
     * @param triggerName
     *          the name of the trigger
     * @param groupName
     *          the group containing the trigger
     * @return the number of rows deleted
     */
    public int deleteBlobTrigger(Connection conn, String triggerName,
            String groupName) throws SQLException {
        PreparedStatement ps = null;

        try {
            ps = conn.prepareStatement(rtp(DELETE_BLOB_TRIGGER));
            ps.setString(1, triggerName);
            ps.setString(2, groupName);

            return ps.executeUpdate();
        } finally {
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    /**
     * <p>
     * Delete the base trigger data for a trigger.
     * </p>
     * 
     * @param conn
     *          the DB Connection
     * @param triggerName
     *          the name of the trigger
     * @param groupName
     *          the group containing the trigger
     * @return the number of rows deleted
     */
    public int deleteTrigger(Connection conn, String triggerName,
            String groupName) throws SQLException {
        PreparedStatement ps = null;

        try {
            ps = conn.prepareStatement(rtp(DELETE_TRIGGER));
            ps.setString(1, triggerName);
            ps.setString(2, groupName);

            return ps.executeUpdate();
        } finally {
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    /**
     * <p>
     * Select the number of triggers associated with a given job.
     * </p>
     * 
     * @param conn
     *          the DB Connection
     * @param jobName
     *          the name of the job
     * @param groupName
     *          the group containing the job
     * @return the number of triggers for the given job
     */
    public int selectNumTriggersForJob(Connection conn, String jobName,
            String groupName) throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            ps = conn.prepareStatement(rtp(SELECT_NUM_TRIGGERS_FOR_JOB));
            ps.setString(1, jobName);
            ps.setString(2, groupName);
            rs = ps.executeQuery();

            if (rs.next()) {
                return rs.getInt(1);
            } else {
                return 0;
            }
        } finally {
            if (null != rs) {
                try {
                    rs.close();
                } catch (SQLException ignore) {
                }
            }
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    /**
     * <p>
     * Select the job to which the trigger is associated.
     * </p>
     * 
     * @param conn
     *          the DB Connection
     * @param triggerName
     *          the name of the trigger
     * @param groupName
     *          the group containing the trigger
     * @return the <code>{@link org.quartz.JobDetail}</code> object
     *         associated with the given trigger
     * @throws SQLException
     * @throws ClassNotFoundException
     */
    public JobDetail selectJobForTrigger(Connection conn, String triggerName,
            String groupName, ClassLoadHelper loadHelper) throws ClassNotFoundException, SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            ps = conn.prepareStatement(rtp(SELECT_JOB_FOR_TRIGGER));
            ps.setString(1, triggerName);
            ps.setString(2, groupName);
            rs = ps.executeQuery();

            if (rs.next()) {
                JobDetail job = new JobDetail();
                job.setName(rs.getString(1));
                job.setGroup(rs.getString(2));
                job.setDurability(rs.getBoolean(3));
                job.setJobClass(loadHelper.loadClass(rs
                        .getString(4)));
                job.setRequestsRecovery(rs.getBoolean(5));
                
                return job;
            } else {
                logger.debug("No job for trigger '" + groupName + "."
                        + triggerName + "'.");
                return null;
            }
        } finally {
            if (null != rs) {
                try {
                    rs.close();
                } catch (SQLException ignore) {
                }
            }
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    /**
     * <p>
     * Select the triggers for a job
     * </p>
     * 
     * @param conn
     *          the DB Connection
     * @param jobName
     *          the name of the trigger
     * @param groupName
     *          the group containing the trigger
     * @return an array of <code>(@link org.quartz.Trigger)</code> objects
     *         associated with a given job.
     * @throws SQLException
     */
    public Trigger[] selectTriggersForJob(Connection conn, String jobName,
            String groupName) throws SQLException, ClassNotFoundException,
            IOException {

        ArrayList trigList = new ArrayList();
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            ps = conn.prepareStatement(rtp(SELECT_TRIGGERS_FOR_JOB));
            ps.setString(1, jobName);
            ps.setString(2, groupName);
            rs = ps.executeQuery();

            while (rs.next()) {
                trigList.add(selectTrigger(conn,
                        rs.getString(COL_TRIGGER_NAME), rs
                                .getString(COL_TRIGGER_GROUP)));
            }
        } finally {
            if (null != rs) {
                try {
                    rs.close();
                } catch (SQLException ignore) {
                }
            }
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }

        return (Trigger[]) trigList.toArray(new Trigger[trigList.size()]);
    }

    public Trigger[] selectTriggersForCalendar(Connection conn, String calName)
        throws SQLException, ClassNotFoundException, IOException {

        ArrayList trigList = new ArrayList();
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            ps = conn.prepareStatement(rtp(SELECT_TRIGGERS_FOR_CALENDAR));
            ps.setString(1, calName);
            rs = ps.executeQuery();

            while (rs.next()) {
                trigList.add(selectTrigger(conn,
                        rs.getString(COL_TRIGGER_NAME), rs
                                .getString(COL_TRIGGER_GROUP)));
            }
        } finally {
            if (null != rs) {
                try {
                    rs.close();
                } catch (SQLException ignore) {
                }
            }
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }

        return (Trigger[]) trigList.toArray(new Trigger[trigList.size()]);
    }
    
    public List selectStatefulJobsOfTriggerGroup(Connection conn,
            String groupName) throws SQLException {
        ArrayList jobList = new ArrayList();
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            ps = conn
                    .prepareStatement(rtp(SELECT_STATEFUL_JOBS_OF_TRIGGER_GROUP));
            ps.setString(1, groupName);
            ps.setBoolean(2, true);
            rs = ps.executeQuery();

            while (rs.next()) {
                jobList.add(new Key(rs.getString(COL_JOB_NAME), rs
                        .getString(COL_JOB_GROUP)));
            }
        } finally {
            if (null != rs) {
                try {
                    rs.close();
                } catch (SQLException ignore) {
                }
            }
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }

        return jobList;
    }

    /**
     * <p>
     * Select a trigger.
     * </p>
     * 
     * @param conn
     *          the DB Connection
     * @param triggerName
     *          the name of the trigger
     * @param groupName
     *          the group containing the trigger
     * @return the <code>{@link org.quartz.Trigger}</code> object
     */
    public Trigger selectTrigger(Connection conn, String triggerName,
            String groupName) throws SQLException, ClassNotFoundException,
            IOException {
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            Trigger trigger = null;

            ps = conn.prepareStatement(rtp(SELECT_TRIGGER));
            ps.setString(1, triggerName);
            ps.setString(2, groupName);
            rs = ps.executeQuery();

            if (rs.next()) {
                String jobName = rs.getString(COL_JOB_NAME);
                String jobGroup = rs.getString(COL_JOB_GROUP);
                boolean volatility = rs.getBoolean(COL_IS_VOLATILE);
                String description = rs.getString(COL_DESCRIPTION);
                long nextFireTime = rs.getLong(COL_NEXT_FIRE_TIME);
                long prevFireTime = rs.getLong(COL_PREV_FIRE_TIME);
                String triggerType = rs.getString(COL_TRIGGER_TYPE);
                long startTime = rs.getLong(COL_START_TIME);
                long endTime = rs.getLong(COL_END_TIME);
                String calendarName = rs.getString(COL_CALENDAR_NAME);
                int misFireInstr = rs.getInt(COL_MISFIRE_INSTRUCTION);

                Map map = null;
                if (canUseProperties()) map = getMapFromProperties(rs);
                else
                    map = (Map) getObjectFromBlob(rs, COL_JOB_DATAMAP);
                
                Date nft = null;
                if (nextFireTime > 0) {
                    nft = new Date(nextFireTime);
                }
                Date pft = null;
                if (prevFireTime > 0) {
                    pft = new Date(prevFireTime);
                }
                Date startTimeD = new Date(startTime);
                Date endTimeD = null;
                if (endTime > 0) {
                    endTimeD = new Date(endTime);
                }

                rs.close();
                ps.close();

                if (triggerType.equals(TTYPE_SIMPLE)) {
                    ps = conn.prepareStatement(rtp(SELECT_SIMPLE_TRIGGER));
                    ps.setString(1, triggerName);
                    ps.setString(2, groupName);
                    rs = ps.executeQuery();

                    if (rs.next()) {
                        int repeatCount = rs.getInt(COL_REPEAT_COUNT);
                        long repeatInterval = rs.getLong(COL_REPEAT_INTERVAL);
                        int timesTriggered = rs.getInt(COL_TIMES_TRIGGERED);

                        SimpleTrigger st = new SimpleTrigger(triggerName,
                                groupName, jobName, jobGroup, startTimeD,
                                endTimeD, repeatCount, repeatInterval);
                        st.setCalendarName(calendarName);
                        st.setMisfireInstruction(misFireInstr);
                        st.setTimesTriggered(timesTriggered);
                        st.setVolatility(volatility);
                        st.setNextFireTime(nft);
                        st.setPreviousFireTime(pft);
                        st.setDescription(description);
                        if (null != map) {
                            st.setJobDataMap(new JobDataMap(map));
                        }
                        trigger = st;
                    }
                } else if (triggerType.equals(TTYPE_CRON)) {
                    ps = conn.prepareStatement(rtp(SELECT_CRON_TRIGGER));
                    ps.setString(1, triggerName);
                    ps.setString(2, groupName);
                    rs = ps.executeQuery();

                    if (rs.next()) {
                        String cronExpr = rs.getString(COL_CRON_EXPRESSION);
                        String timeZoneId = rs.getString(COL_TIME_ZONE_ID);

                        CronTrigger ct = null;
                        try {
                            TimeZone timeZone = null;
                            if (timeZoneId != null) {
                                timeZone = TimeZone.getTimeZone(timeZoneId);
                            }
                            ct = new CronTrigger(triggerName, groupName,
                                    jobName, jobGroup, startTimeD, endTimeD,
                                    cronExpr, timeZone);
                        } catch (Exception neverHappens) {
                            // expr must be valid, or it never would have
                            // gotten to the store...
                        }
                        if (null != ct) {
                            ct.setCalendarName(calendarName);
                            ct.setMisfireInstruction(misFireInstr);
                            ct.setVolatility(volatility);
                            ct.setNextFireTime(nft);
                            ct.setPreviousFireTime(pft);
                            ct.setDescription(description);
                            if (null != map) {
                                ct.setJobDataMap(new JobDataMap(map));
                            }
                            trigger = ct;
                        }
                    }
                } else if (triggerType.equals(TTYPE_BLOB)) {
                    ps = conn.prepareStatement(rtp(SELECT_BLOB_TRIGGER));
                    ps.setString(1, triggerName);
                    ps.setString(2, groupName);
                    rs = ps.executeQuery();

                    if (rs.next()) {
                        trigger = (Trigger) getObjectFromBlob(rs, COL_BLOB);
                    }
                } else {
                    throw new ClassNotFoundException("class for trigger type '"
                            + triggerType + "' not found.");
                }
            }

            return trigger;
        } finally {
            if (null != rs) {
                try {
                    rs.close();
                } catch (SQLException ignore) {
                }
            }
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    /**
     * <p>
     * Select a trigger's JobDataMap.
     * </p>
     * 
     * @param conn
     *          the DB Connection
     * @param triggerName
     *          the name of the trigger
     * @param groupName
     *          the group containing the trigger
     * @return the <code>{@link org.quartz.JobDataMap}</code> of the Trigger,
     * never null, but possibly empty.
     */
    public JobDataMap selectTriggerJobDataMap(Connection conn, String triggerName,
            String groupName) throws SQLException, ClassNotFoundException,
            IOException {
        
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            Trigger trigger = null;

            ps = conn.prepareStatement(rtp(SELECT_TRIGGER_DATA));
            ps.setString(1, triggerName);
            ps.setString(2, groupName);
            rs = ps.executeQuery();

            if (rs.next()) {

                Map map = null;
                if (canUseProperties()) 
                    map = getMapFromProperties(rs);
                else
                    map = (Map) getObjectFromBlob(rs, COL_JOB_DATAMAP);
                
                rs.close();
                ps.close();

                if (null != map) {
                    return new JobDataMap(map);
                }
            }
        } finally {
            if (null != rs) {
                try {
                    rs.close();
                } catch (SQLException ignore) {
                }
            }
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
        
        return new JobDataMap();
    }
            

    /**
     * <p>
     * Select a trigger' state value.
     * </p>
     * 
     * @param conn
     *          the DB Connection
     * @param triggerName
     *          the name of the trigger
     * @param groupName
     *          the group containing the trigger
     * @return the <code>{@link org.quartz.Trigger}</code> object
     */
    public String selectTriggerState(Connection conn, String triggerName,
            String groupName) throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            String state = null;

            ps = conn.prepareStatement(rtp(SELECT_TRIGGER_STATE));
            ps.setString(1, triggerName);
            ps.setString(2, groupName);
            rs = ps.executeQuery();

            if (rs.next()) {
                state = rs.getString(COL_TRIGGER_STATE);
            } else
                state = STATE_DELETED;

            return state.intern();
        } finally {
            if (null != rs) {
                try {
                    rs.close();
                } catch (SQLException ignore) {
                }
            }
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }

    }

    /**
     * <p>
     * Select a trigger' status (state & next fire time).
     * </p>
     * 
     * @param conn
     *          the DB Connection
     * @param triggerName
     *          the name of the trigger
     * @param groupName
     *          the group containing the trigger
     * @return a <code>TriggerStatus</code> object, or null
     */
    public TriggerStatus selectTriggerStatus(Connection conn,
            String triggerName, String groupName) throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            TriggerStatus status = null;

            ps = conn.prepareStatement(rtp(SELECT_TRIGGER_STATUS));
            ps.setString(1, triggerName);
            ps.setString(2, groupName);
            rs = ps.executeQuery();

            if (rs.next()) {
                String state = rs.getString(COL_TRIGGER_STATE);
                long nextFireTime = rs.getLong(COL_NEXT_FIRE_TIME);
                String jobName = rs.getString(COL_JOB_NAME);
                String jobGroup = rs.getString(COL_JOB_GROUP);

                Date nft = null;
                if (nextFireTime > 0) {
                    nft = new Date(nextFireTime);
                }

                status = new TriggerStatus(state, nft);
                status.setKey(new Key(triggerName, groupName));
                status.setJobKey(new Key(jobName, jobGroup));
            }

            return status;
        } finally {
            if (null != rs) {
                try {
                    rs.close();
                } catch (SQLException ignore) {
                }
            }
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }

    }

    /**
     * <p>
     * Select the total number of triggers stored.
     * </p>
     * 
     * @param conn
     *          the DB Connection
     * @return the total number of triggers stored
     */
    public int selectNumTriggers(Connection conn) throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            int count = 0;
            ps = conn.prepareStatement(rtp(SELECT_NUM_TRIGGERS));
            rs = ps.executeQuery();

            if (rs.next()) {
                count = rs.getInt(1);
            }

            return count;
        } finally {
            if (null != rs) {
                try {
                    rs.close();
                } catch (SQLException ignore) {
                }
            }
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    /**
     * <p>
     * Select all of the trigger group names that are stored.
     * </p>
     * 
     * @param conn
     *          the DB Connection
     * @return an array of <code>String</code> group names
     */
    public String[] selectTriggerGroups(Connection conn) throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            ps = conn.prepareStatement(rtp(SELECT_TRIGGER_GROUPS));
            rs = ps.executeQuery();

            ArrayList list = new ArrayList();
            while (rs.next()) {
                list.add(rs.getString(1));
            }

            Object[] oArr = list.toArray();
            String[] sArr = new String[oArr.length];
            System.arraycopy(oArr, 0, sArr, 0, oArr.length);
            return sArr;
        } finally {
            if (null != rs) {
                try {
                    rs.close();
                } catch (SQLException ignore) {
                }
            }
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    /**
     * <p>
     * Select all of the triggers contained in a given group.
     * </p>
     * 
     * @param conn
     *          the DB Connection
     * @param groupName
     *          the group containing the triggers
     * @return an array of <code>String</code> trigger names
     */
    public String[] selectTriggersInGroup(Connection conn, String groupName)
            throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            ps = conn.prepareStatement(rtp(SELECT_TRIGGERS_IN_GROUP));
            ps.setString(1, groupName);
            rs = ps.executeQuery();

            ArrayList list = new ArrayList();
            while (rs.next()) {
                list.add(rs.getString(1));
            }

            Object[] oArr = list.toArray();
            String[] sArr = new String[oArr.length];
            System.arraycopy(oArr, 0, sArr, 0, oArr.length);
            return sArr;
        } finally {
            if (null != rs) {
                try {
                    rs.close();
                } catch (SQLException ignore) {
                }
            }
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    public int insertPausedTriggerGroup(Connection conn, String groupName)
            throws SQLException {
        PreparedStatement ps = null;

        try {
            ps = conn.prepareStatement(rtp(INSERT_PAUSED_TRIGGER_GROUP));
            ps.setString(1, groupName);
            int rows = ps.executeUpdate();

            return rows;
        } finally {
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    public int deletePausedTriggerGroup(Connection conn, String groupName)
            throws SQLException {
        PreparedStatement ps = null;

        try {
            ps = conn.prepareStatement(rtp(DELETE_PAUSED_TRIGGER_GROUP));
            ps.setString(1, groupName);
            int rows = ps.executeUpdate();

            return rows;
        } finally {
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    public int deleteAllPausedTriggerGroups(Connection conn)
            throws SQLException {
        PreparedStatement ps = null;

        try {
            ps = conn.prepareStatement(rtp(DELETE_PAUSED_TRIGGER_GROUPS));
            int rows = ps.executeUpdate();

            return rows;
        } finally {
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    public boolean isTriggerGroupPaused(Connection conn, String groupName)
            throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            ps = conn.prepareStatement(rtp(SELECT_PAUSED_TRIGGER_GROUP));
            ps.setString(1, groupName);
            rs = ps.executeQuery();

            return rs.next();
        } finally {
            if (null != rs) {
                try {
                    rs.close();
                } catch (SQLException ignore) {
                }
            }
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    public boolean isExistingTriggerGroup(Connection conn, String groupName)
            throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            ps = conn.prepareStatement(rtp(SELECT_NUM_TRIGGERS_IN_GROUP));
            ps.setString(1, groupName);
            rs = ps.executeQuery();

            if (!rs.next()) return false;

            return (rs.getInt(1) > 0);
        } finally {
            if (null != rs) {
                try {
                    rs.close();
                } catch (SQLException ignore) {
                }
            }
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    //---------------------------------------------------------------------------
    // calendars
    //---------------------------------------------------------------------------

    /**
     * <p>
     * Insert a new calendar.
     * </p>
     * 
     * @param conn
     *          the DB Connection
     * @param calendarName
     *          the name for the new calendar
     * @param calendar
     *          the calendar
     * @return the number of rows inserted
     * @throws IOException
     *           if there were problems serializing the calendar
     */
    public int insertCalendar(Connection conn, String calendarName,
            Calendar calendar) throws IOException, SQLException {
        ByteArrayOutputStream baos = serializeObject(calendar);

        PreparedStatement ps = null;

        try {
            ps = conn.prepareStatement(rtp(INSERT_CALENDAR));
            ps.setString(1, calendarName);
            ps.setBytes(2, baos.toByteArray());

            return ps.executeUpdate();
        } finally {
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    /**
     * <p>
     * Update a calendar.
     * </p>
     * 
     * @param conn
     *          the DB Connection
     * @param calendarName
     *          the name for the new calendar
     * @param calendar
     *          the calendar
     * @return the number of rows updated
     * @throws IOException
     *           if there were problems serializing the calendar
     */
    public int updateCalendar(Connection conn, String calendarName,
            Calendar calendar) throws IOException, SQLException {
        ByteArrayOutputStream baos = serializeObject(calendar);

        PreparedStatement ps = null;

        try {
            ps = conn.prepareStatement(rtp(UPDATE_CALENDAR));
            ps.setBytes(1, baos.toByteArray());
            ps.setString(2, calendarName);

            return ps.executeUpdate();
        } finally {
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    /**
     * <p>
     * Check whether or not a calendar exists.
     * </p>
     * 
     * @param conn
     *          the DB Connection
     * @param calendarName
     *          the name of the calendar
     * @return true if the trigger exists, false otherwise
     */
    public boolean calendarExists(Connection conn, String calendarName)
            throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            ps = conn.prepareStatement(rtp(SELECT_CALENDAR_EXISTENCE));
            ps.setString(1, calendarName);
            rs = ps.executeQuery();

            if (rs.next()) {
                return true;
            } else {
                return false;
            }
        } finally {
            if (null != rs) {
                try {
                    rs.close();
                } catch (SQLException ignore) {
                }
            }
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    /**
     * <p>
     * Select a calendar.
     * </p>
     * 
     * @param conn
     *          the DB Connection
     * @param calendarName
     *          the name of the calendar
     * @return the Calendar
     * @throws ClassNotFoundException
     *           if a class found during deserialization cannot be found be
     *           found
     * @throws IOException
     *           if there were problems deserializing the calendar
     */
    public Calendar selectCalendar(Connection conn, String calendarName)
            throws ClassNotFoundException, IOException, SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            String selCal = rtp(SELECT_CALENDAR);
            ps = conn.prepareStatement(selCal);
            ps.setString(1, calendarName);
            rs = ps.executeQuery();

            Calendar cal = null;
            if (rs.next()) {
                cal = (Calendar) getObjectFromBlob(rs, COL_CALENDAR);
            }
            if (null == cal) {
                logger.warn("Couldn't find calendar with name '" + calendarName
                        + "'.");
            }
            return cal;
        } finally {
            if (null != rs) {
                try {
                    rs.close();
                } catch (SQLException ignore) {
                }
            }
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    /**
     * <p>
     * Check whether or not a calendar is referenced by any triggers.
     * </p>
     * 
     * @param conn
     *          the DB Connection
     * @param calendarName
     *          the name of the calendar
     * @return true if any triggers reference the calendar, false otherwise
     */
    public boolean calendarIsReferenced(Connection conn, String calendarName)
            throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement(rtp(SELECT_REFERENCED_CALENDAR));
            ps.setString(1, calendarName);
            rs = ps.executeQuery();

            if (rs.next()) {
                return true;
            } else {
                return false;
            }
        } finally {
            if (null != rs) {
                try {
                    rs.close();
                } catch (SQLException ignore) {
                }
            }
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    /**
     * <p>
     * Delete a calendar.
     * </p>
     * 
     * @param conn
     *          the DB Connection
     * @param calendarName
     *          the name of the trigger
     * @return the number of rows deleted
     */
    public int deleteCalendar(Connection conn, String calendarName)
            throws SQLException {
        PreparedStatement ps = null;

        try {
            ps = conn.prepareStatement(rtp(DELETE_CALENDAR));
            ps.setString(1, calendarName);

            return ps.executeUpdate();
        } finally {
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    /**
     * <p>
     * Select the total number of calendars stored.
     * </p>
     * 
     * @param conn
     *          the DB Connection
     * @return the total number of calendars stored
     */
    public int selectNumCalendars(Connection conn) throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            int count = 0;
            ps = conn.prepareStatement(rtp(SELECT_NUM_CALENDARS));

            rs = ps.executeQuery();

            if (rs.next()) {
                count = rs.getInt(1);
            }

            return count;
        } finally {
            if (null != rs) {
                try {
                    rs.close();
                } catch (SQLException ignore) {
                }
            }
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    /**
     * <p>
     * Select all of the stored calendars.
     * </p>
     * 
     * @param conn
     *          the DB Connection
     * @return an array of <code>String</code> calendar names
     */
    public String[] selectCalendars(Connection conn) throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            ps = conn.prepareStatement(rtp(SELECT_CALENDARS));
            rs = ps.executeQuery();

            ArrayList list = new ArrayList();
            while (rs.next()) {
                list.add(rs.getString(1));
            }

            Object[] oArr = list.toArray();
            String[] sArr = new String[oArr.length];
            System.arraycopy(oArr, 0, sArr, 0, oArr.length);
            return sArr;
        } finally {
            if (null != rs) {
                try {
                    rs.close();
                } catch (SQLException ignore) {
                }
            }
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    //---------------------------------------------------------------------------
    // trigger firing
    //---------------------------------------------------------------------------

    /**
     * <p>
     * Select the next time that a trigger will be fired.
     * </p>
     * 
     * @param conn
     *          the DB Connection
     * @return the next fire time, or 0 if no trigger will be fired
     */
    public long selectNextFireTime(Connection conn) throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement(rtp(SELECT_NEXT_FIRE_TIME));
            ps.setString(1, STATE_WAITING);
            rs = ps.executeQuery();

            if (rs.next()) {
                return rs.getLong(ALIAS_COL_NEXT_FIRE_TIME);
            } else {
                return 0l;
            }
        } finally {
            if (null != rs) {
                try {
                    rs.close();
                } catch (SQLException ignore) {
                }
            }
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    /**
     * <p>
     * Select the trigger that will be fired at the given fire time.
     * </p>
     * 
     * @param conn
     *          the DB Connection
     * @param fireTime
     *          the time that the trigger will be fired
     * @return a <code>{@link org.quartz.utils.Key}</code> representing the
     *         trigger that will be fired at the given fire time, or null if no
     *         trigger will be fired at that time
     */
    public Key selectTriggerForFireTime(Connection conn, long fireTime)
            throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement(rtp(SELECT_TRIGGER_FOR_FIRE_TIME));
            ps.setString(1, STATE_WAITING);
            ps.setBigDecimal(2, new BigDecimal(String.valueOf(fireTime)));
            rs = ps.executeQuery();

            if (rs.next()) {
                return new Key(rs.getString(COL_TRIGGER_NAME), rs
                        .getString(COL_TRIGGER_GROUP));
            } else {
                return null;
            }
        } finally {
            if (null != rs) {
                try {
                    rs.close();
                } catch (SQLException ignore) {
                }
            }
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    /**
     * <p>
     * Insert a fired trigger.
     * </p>
     * 
     * @param conn
     *          the DB Connection
     * @param trigger
     *          the trigger
     * @param state
     *          the state that the trigger should be stored in
     * @return the number of rows inserted
     */
    public int insertFiredTrigger(Connection conn, Trigger trigger,
            String state, JobDetail job) throws SQLException {
        PreparedStatement ps = null;
        try {
            ps = conn.prepareStatement(rtp(INSERT_FIRED_TRIGGER));
            ps.setString(1, trigger.getFireInstanceId());
            ps.setString(2, trigger.getName());
            ps.setString(3, trigger.getGroup());
            ps.setBoolean(4, trigger.isVolatile());
            ps.setString(5, instanceId);
            ps.setBigDecimal(6, new BigDecimal(String.valueOf(trigger
                    .getNextFireTime().getTime())));
            ps.setString(7, state);
            if (job != null) {
                ps.setString(8, trigger.getJobName());
                ps.setString(9, trigger.getJobGroup());
                ps.setBoolean(10, job.isStateful());
                ps.setBoolean(11, job.requestsRecovery());
            } else {
                ps.setString(8, null);
                ps.setString(9, null);
                ps.setBoolean(10, false);
                ps.setBoolean(11, false);
            }

            return ps.executeUpdate();
        } finally {
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    /**
     * <p>
     * Select the states of all fired-trigger records for a given trigger, or
     * trigger group if trigger name is <code>null</code>.
     * </p>
     * 
     * @return a List of FiredTriggerRecord objects.
     */
    public List selectFiredTriggerRecords(Connection conn, String triggerName,
            String groupName) throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            List lst = new LinkedList();

            if (triggerName != null) {
                ps = conn.prepareStatement(rtp(SELECT_FIRED_TRIGGER));
                ps.setString(1, triggerName);
                ps.setString(2, groupName);
            } else {
                ps = conn.prepareStatement(rtp(SELECT_FIRED_TRIGGER_GROUP));
                ps.setString(1, groupName);
            }
            rs = ps.executeQuery();

            while (rs.next()) {
                FiredTriggerRecord rec = new FiredTriggerRecord();

                rec.setFireInstanceId(rs.getString(COL_ENTRY_ID));
                rec.setFireInstanceState(rs.getString(COL_ENTRY_STATE));
                rec.setFireTimestamp(rs.getLong(COL_FIRED_TIME));
                rec.setSchedulerInstanceId(rs.getString(COL_INSTANCE_NAME));
                rec.setTriggerIsVolatile(rs.getBoolean(COL_IS_VOLATILE));
                rec.setTriggerKey(new Key(rs.getString(COL_TRIGGER_NAME), rs
                        .getString(COL_TRIGGER_GROUP)));
                if (!rec.getFireInstanceState().equals(STATE_ACQUIRED)) {
                    rec.setJobIsStateful(rs.getBoolean(COL_IS_STATEFUL));
                    rec.setJobRequestsRecovery(rs
                            .getBoolean(COL_REQUESTS_RECOVERY));
                    rec.setJobKey(new Key(rs.getString(COL_JOB_NAME), rs
                            .getString(COL_JOB_GROUP)));
                }
                lst.add(rec);
            }

            return lst;
        } finally {
            if (null != rs) {
                try {
                    rs.close();
                } catch (SQLException ignore) {
                }
            }
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    /**
     * <p>
     * Select the states of all fired-trigger records for a given job, or job
     * group if job name is <code>null</code>.
     * </p>
     * 
     * @return a List of FiredTriggerRecord objects.
     */
    public List selectFiredTriggerRecordsByJob(Connection conn, String jobName,
            String groupName) throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            List lst = new LinkedList();

            if (jobName != null) {
                ps = conn.prepareStatement(rtp(SELECT_FIRED_TRIGGERS_OF_JOB));
                ps.setString(1, jobName);
                ps.setString(2, groupName);
            } else {
                ps = conn
                        .prepareStatement(rtp(SELECT_FIRED_TRIGGERS_OF_JOB_GROUP));
                ps.setString(1, groupName);
            }
            rs = ps.executeQuery();

            while (rs.next()) {
                FiredTriggerRecord rec = new FiredTriggerRecord();

                rec.setFireInstanceId(rs.getString(COL_ENTRY_ID));
                rec.setFireInstanceState(rs.getString(COL_ENTRY_STATE));
                rec.setFireTimestamp(rs.getLong(COL_FIRED_TIME));
                rec.setSchedulerInstanceId(rs.getString(COL_INSTANCE_NAME));
                rec.setTriggerIsVolatile(rs.getBoolean(COL_IS_VOLATILE));
                rec.setTriggerKey(new Key(rs.getString(COL_TRIGGER_NAME), rs
                        .getString(COL_TRIGGER_GROUP)));
                if (!rec.getFireInstanceState().equals(STATE_ACQUIRED)) {
                    rec.setJobIsStateful(rs.getBoolean(COL_IS_STATEFUL));
                    rec.setJobRequestsRecovery(rs
                            .getBoolean(COL_REQUESTS_RECOVERY));
                    rec.setJobKey(new Key(rs.getString(COL_JOB_NAME), rs
                            .getString(COL_JOB_GROUP)));
                }
                lst.add(rec);
            }

            return lst;
        } finally {
            if (null != rs) {
                try {
                    rs.close();
                } catch (SQLException ignore) {
                }
            }
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }

    }

    public List selectInstancesFiredTriggerRecords(Connection conn,
            String instanceName) throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            List lst = new LinkedList();

            ps = conn.prepareStatement(rtp(SELECT_INSTANCES_FIRED_TRIGGERS));
            ps.setString(1, instanceName);
            rs = ps.executeQuery();

            while (rs.next()) {
                FiredTriggerRecord rec = new FiredTriggerRecord();

                rec.setFireInstanceId(rs.getString(COL_ENTRY_ID));
                rec.setFireInstanceState(rs.getString(COL_ENTRY_STATE));
                rec.setFireTimestamp(rs.getLong(COL_FIRED_TIME));
                rec.setSchedulerInstanceId(rs.getString(COL_INSTANCE_NAME));
                rec.setTriggerIsVolatile(rs.getBoolean(COL_IS_VOLATILE));
                rec.setTriggerKey(new Key(rs.getString(COL_TRIGGER_NAME), rs
                        .getString(COL_TRIGGER_GROUP)));
                if (!rec.getFireInstanceState().equals(STATE_ACQUIRED)) {
                    rec.setJobIsStateful(rs.getBoolean(COL_IS_STATEFUL));
                    rec.setJobRequestsRecovery(rs
                            .getBoolean(COL_REQUESTS_RECOVERY));
                    rec.setJobKey(new Key(rs.getString(COL_JOB_NAME), rs
                            .getString(COL_JOB_GROUP)));
                }
                lst.add(rec);
            }

            return lst;
        } finally {
            if (null != rs) {
                try {
                    rs.close();
                } catch (SQLException ignore) {
                }
            }
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    /**
     * <p>
     * Delete a fired trigger.
     * </p>
     * 
     * @param conn
     *          the DB Connection
     * @param entryId
     *          the fired trigger entry to delete
     * @return the number of rows deleted
     */
    public int deleteFiredTrigger(Connection conn, String entryId)
            throws SQLException {
        PreparedStatement ps = null;
        try {
            ps = conn.prepareStatement(rtp(DELETE_FIRED_TRIGGER));
            ps.setString(1, entryId);

            return ps.executeUpdate();
        } finally {
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    public int selectJobExecutionCount(Connection conn, String jobName,
            String jobGroup) throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            ps = conn.prepareStatement(rtp(SELECT_JOB_EXECUTION_COUNT));
            ps.setString(1, jobName);
            ps.setString(2, jobGroup);

            rs = ps.executeQuery();

            if (rs.next()) return rs.getInt(1);
            else
                return 0;

        } finally {
            if (null != rs) {
                try {
                    rs.close();
                } catch (SQLException ignore) {
                }
            }
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    public int deleteVolatileFiredTriggers(Connection conn) throws SQLException {
        PreparedStatement ps = null;
        try {
            ps = conn.prepareStatement(rtp(DELETE_VOLATILE_FIRED_TRIGGERS));
            ps.setBoolean(1, true);

            return ps.executeUpdate();
        } finally {
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    public int insertSchedulerState(Connection conn, String instanceId,
            long checkInTime, long interval, String recoverer)
            throws SQLException {
        PreparedStatement ps = null;
        try {
            ps = conn.prepareStatement(rtp(INSERT_SCHEDULER_STATE));
            ps.setString(1, instanceId);
            ps.setLong(2, checkInTime);
            ps.setLong(3, interval);
            ps.setString(4, recoverer);

            return ps.executeUpdate();
        } finally {
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    public int deleteSchedulerState(Connection conn, String instanceId)
            throws SQLException {
        PreparedStatement ps = null;
        try {
            ps = conn.prepareStatement(rtp(DELETE_SCHEDULER_STATE));
            ps.setString(1, instanceId);

            return ps.executeUpdate();
        } finally {
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    public List selectSchedulerStateRecords(Connection conn, String instanceId)
            throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            List lst = new LinkedList();

            if (instanceId != null) {
                ps = conn.prepareStatement(rtp(SELECT_SCHEDULER_STATE));
                ps.setString(1, instanceId);
            } else {
                ps = conn.prepareStatement(rtp(SELECT_SCHEDULER_STATES));
            }
            rs = ps.executeQuery();

            while (rs.next()) {
                SchedulerStateRecord rec = new SchedulerStateRecord();

                rec.setSchedulerInstanceId(rs.getString(COL_INSTANCE_NAME));
                rec.setCheckinTimestamp(rs.getLong(COL_LAST_CHECKIN_TIME));
                rec.setCheckinInterval(rs.getLong(COL_CHECKIN_INTERVAL));
                rec.setRecoverer(rs.getString(COL_RECOVERER));

                lst.add(rec);
            }

            return lst;
        } finally {
            if (null != rs) {
                try {
                    rs.close();
                } catch (SQLException ignore) {
                }
            }
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }

    }

    //---------------------------------------------------------------------------
    // protected methods that can be overridden by subclasses
    //---------------------------------------------------------------------------

    /**
     * <p>
     * Replace the table prefix in a query by replacing any occurrences of
     * "{0}" with the table prefix.
     * </p>
     * 
     * @param query
     *          the unsubstitued query
     * @return the query, with proper table prefix substituted
     */
    protected final String rtp(String query) {
        return Util.rtp(query, tablePrefix);
    }

    /**
     * <p>
     * Create a serialized <code>java.util.ByteArrayOutputStream</code>
     * version of an Object.
     * </p>
     * 
     * @param obj
     *          the object to serialize
     * @return the serialized ByteArrayOutputStream
     * @throws IOException
     *           if serialization causes an error
     */
    protected ByteArrayOutputStream serializeObject(Object obj)
            throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        if (null != obj) {
            ObjectOutputStream out = new ObjectOutputStream(baos);
            out.writeObject(obj);
            out.flush();
        }
        return baos;
    }

    /**
     * <p>
     * Remove the transient data from and then create a serialized <code>java.util.ByteArrayOutputStream</code>
     * version of a <code>{@link org.quartz.JobDataMap}</code>.
     * </p>
     * 
     * @param obj
     *          the object to serialize
     * @return the serialized ByteArrayOutputStream
     * @throws IOException
     *           if serialization causes an error
     */
    protected ByteArrayOutputStream serializeJobData(JobDataMap data)
            throws IOException {
        if (canUseProperties()) return serializeProperties(data);

        if (null != data) {
            data.removeTransientData();
            return serializeObject(data);
        } else {
            return serializeObject(null);
        }
    }

    /**
     * serialize the java.util.Properties
     */
    private ByteArrayOutputStream serializeProperties(JobDataMap data)
            throws IOException {
        ByteArrayOutputStream ba = new ByteArrayOutputStream();
        if (null != data) {
            Properties properties = convertToProperty(data.getWrappedMap());
            properties.store(ba, "");
        }

        return ba;
    }

    /**
     * convert the JobDataMap into a list of properties
     */
    protected Map convertFromProperty(Properties properties) throws IOException {
        Map data = new HashMap();
        Set keys = properties.keySet();
        Iterator it = keys.iterator();
        while (it.hasNext()) {
            Object key = it.next();
            Object val = properties.get(key);
            data.put(key, val);
        }

        return data;
    }

    /**
     * convert the JobDataMap into a list of properties
     */
    protected Properties convertToProperty(Map data) throws IOException {
        Properties properties = new Properties();
        Set keys = data.keySet();
        Iterator it = keys.iterator();
        while (it.hasNext()) {
            Object key = it.next();
            Object val = data.get(key);
            if(!(key instanceof String))
                throw new IOException("JobDataMap keys/values must be Strings " 
                        + "when the 'useProperties' property is set. " 
                        + " offending Key: " + key);
            if(!(val instanceof String))
                throw new IOException("JobDataMap values must be Strings " 
                        + "when the 'useProperties' property is set. " 
                        + " Key of offending value: " + key);
            if (val == null) val = "";
            properties.put(key, val);
        }
        return properties;
    }

    /**
     * <p>
     * This method should be overridden by any delegate subclasses that need
     * special handling for BLOBs. The default implementation uses standard
     * JDBC <code>java.sql.Blob</code> operations.
     * </p>
     * 
     * @param rs
     *          the result set, already queued to the correct row
     * @param colName
     *          the column name for the BLOB
     * @return the deserialized Object from the ResultSet BLOB
     * @throws ClassNotFoundException
     *           if a class found during deserialization cannot be found
     * @throws IOException
     *           if deserialization causes an error
     */
    protected Object getObjectFromBlob(ResultSet rs, String colName)
            throws ClassNotFoundException, IOException, SQLException {
        Object obj = null;

        Blob blobLocator = rs.getBlob(colName);
        if (blobLocator != null) {
            InputStream binaryInput = blobLocator.getBinaryStream();

            if (null != binaryInput) {
                ObjectInputStream in = new ObjectInputStream(binaryInput);
                obj = in.readObject();
                in.close();
            }
        }
        return obj;
    }

    public Key[] selectVolatileTriggers(Connection conn) throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            ps = conn.prepareStatement(rtp(SELECT_VOLATILE_TRIGGERS));
            ps.setBoolean(1, true);
            rs = ps.executeQuery();

            ArrayList list = new ArrayList();
            while (rs.next()) {
                String triggerName = rs.getString(COL_TRIGGER_NAME);
                String groupName = rs.getString(COL_TRIGGER_GROUP);
                list.add(new Key(triggerName, groupName));
            }
            Object[] oArr = list.toArray();
            Key[] kArr = new Key[oArr.length];
            System.arraycopy(oArr, 0, kArr, 0, oArr.length);
            return kArr;
        } finally {
            if (null != rs) {
                try {
                    rs.close();
                } catch (SQLException ignore) {
                }
            }
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    public Key[] selectVolatileJobs(Connection conn) throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            ps = conn.prepareStatement(rtp(SELECT_VOLATILE_JOBS));
            ps.setBoolean(1, true);
            rs = ps.executeQuery();

            ArrayList list = new ArrayList();
            while (rs.next()) {
                String triggerName = rs.getString(COL_JOB_NAME);
                String groupName = rs.getString(COL_JOB_GROUP);
                list.add(new Key(triggerName, groupName));
            }
            Object[] oArr = list.toArray();
            Key[] kArr = new Key[oArr.length];
            System.arraycopy(oArr, 0, kArr, 0, oArr.length);
            return kArr;
        } finally {
            if (null != rs) {
                try {
                    rs.close();
                } catch (SQLException ignore) {
                }
            }
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    /**
     * <p>
     * This method should be overridden by any delegate subclasses that need
     * special handling for BLOBs for job details. The default implementation
     * uses standard JDBC <code>java.sql.Blob</code> operations.
     * </p>
     * 
     * @param rs
     *          the result set, already queued to the correct row
     * @param colName
     *          the column name for the BLOB
     * @return the deserialized Object from the ResultSet BLOB
     * @throws ClassNotFoundException
     *           if a class found during deserialization cannot be found
     * @throws IOException
     *           if deserialization causes an error
     */
    protected Object getJobDetailFromBlob(ResultSet rs, String colName)
            throws ClassNotFoundException, IOException, SQLException {
        if (canUseProperties()) {
            Blob blobLocator = rs.getBlob(colName);
            if (blobLocator != null) {
                InputStream binaryInput = blobLocator.getBinaryStream();
                return binaryInput;
            } else {
                return null;
            }
        }

        return getObjectFromBlob(rs, colName);
    }

    /** 
     * @see org.quartz.impl.jdbcjobstore.DriverDelegate#selectPausedTriggerGroups(java.sql.Connection)
     */
    public Set selectPausedTriggerGroups(Connection conn) throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;

        HashSet set = new HashSet();
        try {
            ps = conn.prepareStatement(rtp(SELECT_PAUSED_TRIGGER_GROUPS));
            rs = ps.executeQuery();

            while (rs.next()) {
                String groupName = rs.getString(COL_TRIGGER_GROUP);
                set.add(groupName);
            }
            return set;
        } finally {
            if (null != rs) {
                try {
                    rs.close();
                } catch (SQLException ignore) {
                }
            }
            if (null != ps) {
                try {
                    ps.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }
}

// EOF
