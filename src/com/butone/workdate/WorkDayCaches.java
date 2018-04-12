package com.butone.workdate;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.justep.system.data.KSQL;
import com.justep.system.data.Row;
import com.justep.system.data.Table;
import com.justep.system.util.CommonUtils;

/**
 * 扩展为工作日管理类。所有工作调整的修改只能通过这一个入口。 方便后期可以通过配置文件，触发工作日调整引起的其他业务逻辑。
 * 
 * @author DaChong
 * 
 */
public class WorkDayCaches {

	private static Map<String, Map<Integer, WorkDate>> adjustedDatesOfYearMonth = new ConcurrentHashMap<String, Map<Integer, WorkDate>>();

	/**
	 * 获得已加载的缓存年份，当工作日调整模块修改数据时，如果当前年已缓存，应刷新cached数据。
	 * 
	 * @return
	 */
	public static void reloadDatesOfYearMonth(int year, int month) {
		adjustedDatesOfYearMonth.remove(year + "-" + month);
		getChangedWorkDates(year, month);
	}

	/**
	 * 刷新指定年份的缓存数据
	 * 
	 * @param year
	 * @return
	 */
	public static Map<Integer, WorkDate> getChangedWorkDates(int year, int month) {
		Map<Integer, WorkDate> dates = adjustedDatesOfYearMonth.get(year + "-" + month);
		if (dates == null) {
			dates = new HashMap<Integer, WorkDate>(31);

			String sql = "select b.* from B_WorkDaysMang b where b.fKind <> '无变化' and b.fYear = :year and b.fMonth=:month";
			Map<String, Object> params = new HashMap<String, Object>();
			params.put("year", year);
			params.put("month", month);
			Table table = KSQL.select(sql, params, "/base/system/workDaysManage/data", null);

			Iterator<Row> i = table.iterator();

			while (i.hasNext()) {
				Row rs = i.next();
				Date fdate = rs.getDate("fDate");
				String fkind = rs.getString("fKind");
				String fdesc = rs.getString("fDesc");
				Integer fyear = rs.getInt("fYear");
				Integer fmonth = rs.getInt("fMonth");
				Integer fday = rs.getInt("fDay");

				WorkDate workDate = new WorkDate(fdate);
				workDate.setYear(fyear);
				workDate.setMonth(fmonth);
				workDate.setDay(fday);
				workDate.setDesc(fdesc);
				workDate.setChangeKind(fkind);
				dates.put(workDate.hashCode(), workDate);
			}
			adjustedDatesOfYearMonth.put(year + "-" + month, dates);
		}
		return dates;
	}

	/**
	 * 是否休息日转工作日
	 * 
	 * @param Date
	 * @return boolean true OR false
	 */
	public static boolean isRestToWorkDay(Date d) {
		if (!isDefaultWrokDay(d)) {
			// 休息日
			Map<Integer, WorkDate> changes = getChangedWorkDates(CommonUtils.getYear(d), CommonUtils.getMonth(d));
			Calendar cal = Calendar.getInstance();
			cal.setTime(d);
			int year = cal.get(Calendar.YEAR);
			int month = (cal.get(Calendar.MONTH) + 1);
			int day = cal.get(Calendar.DATE);
			return changes.containsKey(new WorkDate(year, month, day).hashCode());
		} else {
			return false;
		}
	}

	/**
	 * 是否工作日转休息日
	 * 
	 * @param Date
	 * @return boolean true OR false
	 */
	public static boolean isWorkDayToRest(Date d) {
		if (isDefaultWrokDay(d)) {
			// 工作日
			Map<Integer, WorkDate> changes = getChangedWorkDates(CommonUtils.getYear(d), CommonUtils.getMonth(d));
			Calendar cal = Calendar.getInstance();
			cal.setTime(d);
			int year = cal.get(Calendar.YEAR);
			int month = (cal.get(Calendar.MONTH) + 1);
			int day = cal.get(Calendar.DATE);
			return changes.containsKey(new WorkDate(year, month, day).hashCode());
		} else {
			return false;
		}
	}

	/**
	 * 获得第一个工作日
	 * 
	 * @param d
	 */
	public static Date getRealWorkDate(Date d, boolean after) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(d);
		// 起始日期应修正到工作日
		while (WorkDayCaches.isRestDay(cal.getTime())) {
			cal.add(Calendar.DATE, after ? 1 : -1);
		}
		return cal.getTime();
	}

	/**
	 * 是否休息日
	 * 
	 * @param Date
	 * @return boolean true OR false
	 */
	public static boolean isRestDay(Date d) {
		Map<Integer, WorkDate> changes = getChangedWorkDates(CommonUtils.getYear(d), CommonUtils.getMonth(d));
		Calendar cal = Calendar.getInstance();
		cal.setTime(d);
		int year = cal.get(Calendar.YEAR);
		int month = (cal.get(Calendar.MONTH) + 1);
		int day = cal.get(Calendar.DATE);
		WorkDate workDate = new WorkDate(year, month, day);
		if (isDefaultWrokDay(d)) {
			// 工作日，调整过的就是休息日
			return changes.containsKey(workDate.hashCode());
		} else {
			// 休息日，没调整就是休息日
			return !changes.containsKey(workDate.hashCode());
		}
	}

	/**
	 * 是否工作日
	 * 
	 * @param Date
	 * @return boolean true OR false
	 */
	public static boolean isWorkDate(Date d) {
		return !isRestDay(d);
	}

	/**
	 * 得到给定日期的星期
	 * 
	 * @param Date
	 * @return theWeek 说明：1,2,3,4,5,6,7分别对应星期日，一，二，三，四，五，六
	 */
	public static int getTheWeek(Date d) {
		Calendar calDate = Calendar.getInstance();
		calDate.setTime(d);
		int theWeek = calDate.get(Calendar.DAY_OF_WEEK);
		return theWeek;
	}

	/**
	 * 默认是否工作日
	 * 
	 * @param Date
	 * @return boolean true OR false
	 */
	public static boolean isDefaultWrokDay(Date d) {
		int theWeek = getTheWeek(d);
		// 1=星期日 7=星期六
		return !(theWeek == 1 || theWeek == 7);
	}
}