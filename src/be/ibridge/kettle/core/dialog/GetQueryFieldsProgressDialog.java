
package be.ibridge.kettle.core.dialog;

import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.swt.widgets.Shell;

import be.ibridge.kettle.core.LocalVariables;
import be.ibridge.kettle.core.LogWriter;
import be.ibridge.kettle.core.Props;
import be.ibridge.kettle.core.Row;
import be.ibridge.kettle.core.database.Database;
import be.ibridge.kettle.core.database.DatabaseMeta;


/**
 * Takes care of displaying a dialog that will handle the wait while 
 * we're finding out which fields are output by a certain SQL query on a database.
 * 
 * @author Matt
 * @since  12-may-2005
 */
public class GetQueryFieldsProgressDialog
{
	private Shell shell;
	private DatabaseMeta dbMeta;
	private String sql;
	private Row result;
	
	private Database db;
    private Thread parentThread;

    /**
     * Creates a new dialog that will handle the wait while we're 
     * finding out what tables, views etc we can reach in the database.
     * @deprecated please use the constructor version without log or props
     */
    public GetQueryFieldsProgressDialog(LogWriter log, Props props, Shell shell, DatabaseMeta dbInfo, String sql)
    {
        this(shell, dbInfo, sql);
    }
    
	/**
	 * Creates a new dialog that will handle the wait while we're 
	 * finding out what tables, views etc we can reach in the database.
	 */
	public GetQueryFieldsProgressDialog(Shell shell, DatabaseMeta dbInfo, String sql)
	{
		this.shell = shell;
		this.dbMeta = dbInfo;
		this.sql = sql;
        
        this.parentThread = Thread.currentThread();
	}
	
	public Row open()
	{
		IRunnableWithProgress op = new IRunnableWithProgress()
		{
			public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException
			{
                // This is running in a new process: copy some KettleVariables info
                LocalVariables.getInstance().createKettleVariables(Thread.currentThread().getName(), parentThread.getName(), true);

			    db = new Database(dbMeta);
			    try
				{
        			db.connect();
        			result = db.getQueryFields(sql, false);
					if (monitor.isCanceled())
					{
						throw new InvocationTargetException(new Exception("This operation was cancelled!"));
					}
				}
				catch(Exception e)
				{
					throw new InvocationTargetException(e, "Problem encountered determining query fields: "+e.toString());
				}
				finally
				{
				    db.disconnect();
				}
			}
		};
		
		try
		{
			final ProgressMonitorDialog pmd = new ProgressMonitorDialog(shell);

            // Run something in the background to cancel active database queries, forecably if needed!
			Runnable run = new Runnable()
            {
                public void run()
                {
                    IProgressMonitor monitor = pmd.getProgressMonitor();
                    while (pmd.getShell()==null || ( !pmd.getShell().isDisposed() && !monitor.isCanceled() ))
                    {
                        try { Thread.sleep(250); } catch(InterruptedException e) { };
                    }
                    
                    if (monitor.isCanceled()) // Disconnect and see what happens!
                    {
                        try { db.cancelQuery(); } catch(Exception e) {};
                    }
                }
            };
            // Dump the cancel looker in the background!
            new Thread(run).start();
            
			pmd.run(true, true, op);
		}
		catch (InvocationTargetException e)
		{
			new ErrorDialog(shell, "Error getting information", "An error occured getting information from the database!", e);
			return null;
		}
		catch (InterruptedException e)
		{
			new ErrorDialog(shell, "Error getting information", "An error occured getting information from the database!", e);
			return null;
		}
		
		return result;
	}
}
