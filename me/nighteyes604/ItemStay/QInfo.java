package me.nighteyes604.ItemStay;

public class QInfo
{
	private int id;
	private QResult result;

	QInfo(int id, QResult result)
	{
		this.id = id;
		this.result = result;
	}

	/**
	 * @return The ID of new or removed item display if successful. The ID of conflicting item display if the location is taken. -1 if item was not found during removal.
	 */
	public int getId()
	{
		return id;
	}

	public QResult getResult()
	{
		return result;
	}
}
