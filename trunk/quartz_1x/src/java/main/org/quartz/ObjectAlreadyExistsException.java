/*
 * Copyright (c) 2004-2005 by OpenSymphony
 * All rights reserved.
 * 
 * Previously Copyright (c) 2001-2004 James House
 */
package org.quartz;

/**
 * <p>
 * An exception that is thrown to indicate that an attempt to store a new
 * object (i.e. <code>{@link org.quartz.JobDetail}</code>,<code>{@link Trigger}</code>
 * or <code>{@link Calendar}</code>) in a <code>{@link Scheduler}</code>
 * failed, because one with the same name & group already exists.
 * </p>
 * 
 * @author James House
 */
public class ObjectAlreadyExistsException extends JobPersistenceException {

    /*
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     * 
     * Constructors.
     * 
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     */

    /**
     * <p>
     * Create a <code>ObjectAlreadyExistsException</code> with the given
     * message.
     * </p>
     */
    public ObjectAlreadyExistsException(String msg) {
        super(msg);
    }

    /**
     * <p>
     * Create a <code>ObjectAlreadyExistsException</code> and auto-generate a
     * message using the name/group from the given <code>JobDetail</code>.
     * </p>
     * 
     * <p>
     * The message will read: <BR>"Unable to store Job with name: '__' and
     * group: '__', because one already exists with this identification."
     * </p>
     */
    public ObjectAlreadyExistsException(JobDetail offendingJob) {
        super("Unable to store Job with name: '" + offendingJob.getName()
                + "' and group: '" + offendingJob.getGroup()
                + "', because one already exists with this identification.");
    }

    /**
     * <p>
     * Create a <code>ObjectAlreadyExistsException</code> and auto-generate a
     * message using the name/group from the given <code>Trigger</code>.
     * </p>
     * 
     * <p>
     * The message will read: <BR>"Unable to store Trigger with name: '__' and
     * group: '__', because one already exists with this identification."
     * </p>
     */
    public ObjectAlreadyExistsException(Trigger offendingTrigger) {
        super("Unable to store Trigger with name: '"
                + offendingTrigger.getName() + "' and group: '"
                + offendingTrigger.getGroup()
                + "', because one already exists with this identification.");
    }

}