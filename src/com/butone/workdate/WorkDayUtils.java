package com.butone.workdate;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import com.justep.exception.BusinessException;
import com.justep.system.util.CommonUtils;

public class WorkDayUtils {
	public static void main(String[] args) throws ParseException {
		Calendar cal1 = Calendar.getInstance();
		cal1.setTimeInMillis(System.currentTimeMillis());
		cal1.set(Calendar.HOUR_OF_DAY, 0);
		cal1.set(Calendar.MINUTE, 0);
		cal1.set(Calendar.SECOND, 0);
		// cal1.set(Calendar.MILLISECOND, 0);

		Calendar cal2 = Calendar.getInstance();
		cal2.setTimeInMillis(new SimpleDateFormat("yyyy-mm-dd").parse("2017-01-03").getTime());
		cal2.set(Calendar.HOUR_OF_DAY, 0);
		cal2.set(Calendar.MINUTE, 0);
		cal2.set(Calendar.SECOND, 0);

		System.out.println(cal1.compareTo(cal2));
		System.out.println(compareDate(cal1.getTime(), cal2.getTime(), "工作日"));
	}

	public static int START_OF_AM = 8;
	public static int END_OF_AM = 12;
	public static int END_OF_PM = 18;

	/**
	 * 得到days个工作日后的第一个工作日
	 * 
	 * @param start
	 *            开始日期
	 * @param days
	 *            工作日
	 * @param includeToDay
	 *            是否包含开始日期(默认为否)
	 * @param checkTime
	 *            检查时间部分
	 * @return days个工作日后的第一个工作日
	 * @throws ParseException
	 */
	public static Date getDateAfterWorkDays(Date start, Integer days, boolean includeStart) {
		return WorkDayUtils.getDateAfterWorkDays(start, days, includeStart, false);
	}

	/**
	 * 得到days个工作日后的第一个工作日。days支持小数，半天为单位x.y一律视为x.5
	 * 
	 * @param start
	 * @param days
	 *            支持小数
	 * @param includeStart
	 * @return
	 */
	public static Date getDateAfterWorkDays(Date start, BigDecimal days, boolean includeStart) {
		if (days == null)
			return null;
		if (days.doubleValue() == days.intValue()) {
			return WorkDayUtils.getDateAfterWorkDays(start, days.intValue(), includeStart, false);
		} else {
			if (days.doubleValue() < 0) {
				return WorkDayUtils.getDateAfterWorkDays(start, days.intValue() - 1, includeStart, true);
			} else {
				return WorkDayUtils.getDateAfterWorkDays(start, days.intValue(), includeStart, true);
			}
		}
	}

	/**
	 * 得到days个工作日后的第一个工作日
	 * 
	 * @param start
	 * @param days
	 * @param includeToDay
	 * @param addHalfDay
	 * @return
	 */
	public static Date getDateAfterWorkDays(Date start, Integer days, boolean includeToDay, boolean addHalfDay) {
		if (start == null)
			return null;
		if (days == null)
			return null;

		boolean after = days >= 0, thisIsWorkDate = WorkDayCaches.isWorkDate(start);
		int interval = Math.abs(days);
		// 获得工作日
		Date realStart = thisIsWorkDate ? start : WorkDayCaches.getRealWorkDate(start, after);

		Calendar cal = Calendar.getInstance();
		cal.setTime(realStart);
		// 清除时间部分
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		if (thisIsWorkDate) {
			if (includeToDay) {
				// 当前是工作日
				int h = cal.get(Calendar.HOUR_OF_DAY);
				if (h < END_OF_AM) {
					// 如果上午开始，12点截止
					cal.set(Calendar.HOUR_OF_DAY, END_OF_AM);
				} else {
					// 如果下午开始，18点截止
					cal.set(Calendar.HOUR_OF_DAY, END_OF_PM);
				}
			} else {
				if (interval == 0) {
					// 不含当前 需要下一工作日
					interval++;
					cal.set(Calendar.HOUR_OF_DAY, END_OF_AM);
				} else {
					// 不含当前设置为下午结束
					cal.set(Calendar.HOUR_OF_DAY, END_OF_PM);
				}
			}
		} else {
			// 修正后的下一工作日
			if (interval == 0) {
				cal.set(Calendar.HOUR_OF_DAY, START_OF_AM);
			} else {
				cal.set(Calendar.HOUR_OF_DAY, END_OF_PM);
				// 已经下一工作日，所以需要减去1天
				interval--;
			}
		}
		realStart = cal.getTime();

		// 开始循环天数（如果为0，返回起始日期）
		Date endDate = getWorkDateIntervalDays(realStart, interval, after);
		if (addHalfDay) {
			cal.setTime(endDate);
			int h = cal.get(Calendar.HOUR_OF_DAY);
			if (after) {
				if (h == END_OF_AM) {
					cal.set(Calendar.HOUR_OF_DAY, END_OF_PM);
					endDate = cal.getTime();
				} else if (h == END_OF_PM) {
					cal.set(Calendar.HOUR_OF_DAY, END_OF_AM);
					endDate = getWorkDateIntervalDays(cal.getTime(), 1, true);
				} else if (h == START_OF_AM) {
					cal.set(Calendar.HOUR_OF_DAY, END_OF_AM);
					endDate = cal.getTime();
				} else {
					throw new BusinessException("工作日计算内部错误");
				}
			} else {
				if (h == END_OF_AM) {
					cal.set(Calendar.HOUR_OF_DAY, END_OF_PM);
					endDate = getWorkDateIntervalDays(cal.getTime(), 1, false);
				} else if (h == END_OF_PM) {
					cal.set(Calendar.HOUR_OF_DAY, END_OF_AM);
					endDate = cal.getTime();
				} else if (h == START_OF_AM) {
					cal.set(Calendar.HOUR_OF_DAY, END_OF_AM);
					endDate = getWorkDateIntervalDays(cal.getTime(), 1, false);
				} else {
					throw new BusinessException("工作日计算内部错误");
				}
			}
		}
		return endDate;
	}

	/**
	 * 获得自然间隔工作日后日期，不含开始日期
	 * 
	 * @param start
	 *            开始日期
	 * @param interval
	 *            间隔
	 * @param after
	 *            是否之后
	 * @return
	 */
	private static Date getWorkDateIntervalDays(Date start, int interval, boolean after) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(start);
		Date endDate = start;
		int n = 0;
		while (n < interval) {
			cal.add(Calendar.DATE, after ? 1 : -1);
			endDate = cal.getTime();
			if (WorkDayCaches.isWorkDate(endDate)) {
				n++;
			}
		}
		return endDate;
	}

	/**
	 * 得到days个工作日后的第一个自然日。days支持小数，半天为单位x.y一律视为x.5
	 * 
	 * @param start
	 * @param days
	 *            支持小数
	 * @param includeStart
	 * @return
	 */
	public static Date getDateAfterNatureDays(Date start, BigDecimal days, boolean includeStart) {
		if (days == null)
			return null;
		if (days.doubleValue() == days.intValue()) {
			return WorkDayUtils.getDateAfterNatureDays(start, days.intValue(), includeStart, false);
		} else {
			if (days.doubleValue() < 0) {
				return WorkDayUtils.getDateAfterNatureDays(start, days.intValue() - 1, includeStart, true);
			} else {
				return WorkDayUtils.getDateAfterNatureDays(start, days.intValue(), includeStart, true);
			}
		}
	}

	/**
	 * 得到days个工作日后的第一个自然日。
	 * 
	 * @param start
	 * @param days
	 *            支持小数
	 * @param includeStart
	 * @return
	 */
	public static Date getDateAfterNatureDays(Date start, Integer days, boolean includeStart) {
		return getDateAfterNatureDays(start, days, includeStart, false);
	}

	/**
	 * 得到days个工作日后的第一个自然日。
	 * 
	 * @param start
	 * @param days
	 *            支持小数
	 * @param includeStart
	 * @param addHalfDay
	 *            增加半天
	 * @return
	 */
	public static Date getDateAfterNatureDays(Date start, Integer days, boolean includeStart, boolean addHalfDay) {
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(start);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		int hour = calendar.get(Calendar.HOUR_OF_DAY);
		boolean after = days >= 0;
		if (includeStart) {
			if (hour < END_OF_AM) {
				// 如果是上午开始，修正开始时间到12点
				calendar.set(Calendar.HOUR_OF_DAY, END_OF_AM);
			} else {
				// 如果是下午，修正开始时间到18点
				calendar.set(Calendar.HOUR_OF_DAY, END_OF_PM);
			}
			calendar.add(Calendar.DATE, days);
		} else {
			calendar.add(Calendar.DATE, days);
			if (days == 0) {
				// 修正为隔天
				calendar.add(Calendar.DATE, 1);
				calendar.set(Calendar.HOUR_OF_DAY, START_OF_AM);
			} else {
				calendar.set(Calendar.HOUR_OF_DAY, END_OF_PM);
			}
		}
		if (addHalfDay) {
			hour = calendar.get(Calendar.HOUR_OF_DAY);
			if (after) {
				if (hour == END_OF_AM) {
					calendar.set(Calendar.HOUR_OF_DAY, END_OF_PM);
				} else if (hour == END_OF_PM) {
					calendar.add(Calendar.DATE, 1);
					calendar.set(Calendar.HOUR_OF_DAY, END_OF_AM);
				} else if (hour == START_OF_AM) {
					calendar.set(Calendar.HOUR_OF_DAY, END_OF_AM);
				} else {
					throw new BusinessException("工作日计算内部错误");
				}
			} else {
				if (hour == END_OF_AM) {
					calendar.add(Calendar.DATE, -1);
					calendar.set(Calendar.HOUR_OF_DAY, END_OF_PM);
				} else if (hour == END_OF_PM) {
					calendar.set(Calendar.HOUR_OF_DAY, END_OF_AM);
				} else if (hour == START_OF_AM) {
					calendar.add(Calendar.DATE, -1);
					calendar.set(Calendar.HOUR_OF_DAY, END_OF_AM);
				} else {
					throw new BusinessException("工作日计算内部错误");
				}

			}
		}

		return calendar.getTime();
	}

	/**
	 * 计算开始日期后多少天的日期
	 * 
	 * @param start
	 * @param days
	 * @param limitKind
	 * @param includeStart
	 * @return
	 */
	public static Date calcDateAfterDays(Date start, BigDecimal days, String limitKind, boolean includeStart) {
		if (days == null)
			return null;
		if ("自然日".equals(limitKind)) {
			return WorkDayUtils.getDateAfterNatureDays(start, days, includeStart);
		} else {
			return WorkDayUtils.getDateAfterWorkDays(start, days, includeStart);
		}
	}

	public static Date calcDateAfterDays(Date start, int days, String limitKind, boolean includeStart) {
		if ("自然日".equals(limitKind)) {
			return WorkDayUtils.getDateAfterNatureDays(start, days, includeStart);
		} else {
			return WorkDayUtils.getDateAfterWorkDays(start, days, includeStart);
		}
	}

	/**
	 * 计算剩余天数
	 * 
	 * @param startDate
	 * @param endDate
	 * @param kind
	 * @param half
	 * @return
	 */
	public static Double remainedDays(Date startTime, Date endTime, String kind, boolean half) {

		if (startTime == null || endTime == null)
			return null;
		boolean isNegativeNumber = compareDate(startTime, endTime, kind) > 0;
		// 如果开始大于结束，交换开始解释
		if (isNegativeNumber) {
			Date tmp = new Date(startTime.getTime());
			startTime = endTime;
			endTime = tmp;
		}
		Calendar cal = Calendar.getInstance();
		cal.setTime(startTime);
		int startHour = cal.get(Calendar.HOUR_OF_DAY);
		cal.setTime(endTime);
		int endHour = cal.get(Calendar.HOUR_OF_DAY);
		if ("自然日".equals(kind)) {
			// TODO 有问题
			Double remainedDays = new Double(Math.abs(CommonUtils.dateDiff("day", startTime, endTime)));
			if (half)
				remainedDays = fiexNatureRemainedDays(remainedDays, startHour, endHour);
			return (isNegativeNumber ? -1 : 1) * remainedDays;
		} else {
			Double remainedDays = new Double(WorkDayUtils.calcWorkDaysBetween(startTime, endTime));
			if (remainedDays == 0)
				return null;
			if (half) {
				remainedDays = fiexWorkDateRemainedDays(remainedDays, startHour, endHour);
			} else
				remainedDays--;
			return (isNegativeNumber ? -1 : 1) * remainedDays;
		}
	}

	/**
	 * 计算两个日期之间的工作日数
	 * 
	 * @param startDate
	 *            开始日期
	 * @param endDate
	 *            结束日期
	 * @return int
	 */
	public static Long calcWorkDaysBetween(Date startDate, Date endDate) {
		if (startDate == null || endDate == null)
			return null;

		if (compareDate(startDate, endDate, "工作日") > 0) {
			// 交换开始结束时间
			Date tmp_End = new Date(endDate.getTime());
			endDate = startDate;
			startDate = tmp_End;
		}

		Calendar calSta = Calendar.getInstance();
		calSta.setTime(startDate);
		calSta.set(Calendar.HOUR_OF_DAY, 0);
		calSta.set(Calendar.SECOND, 0);
		calSta.set(Calendar.MINUTE, 0);
		calSta.set(Calendar.MILLISECOND, 0);

		Calendar calEnd = Calendar.getInstance();
		calEnd.setTime(endDate);
		calEnd.set(Calendar.HOUR_OF_DAY, 0);
		calEnd.set(Calendar.SECOND, 0);
		calEnd.set(Calendar.MINUTE, 0);
		calEnd.set(Calendar.MILLISECOND, 0);

		long days = 0;
		while (calSta.compareTo(calEnd) <= 0) {
			if (WorkDayCaches.isDefaultWrokDay(calSta.getTime()) && !WorkDayCaches.isWorkDayToRest(calSta.getTime())) {
				// 工作日 且未调整为休息日
				days++;
			} else if (!WorkDayCaches.isDefaultWrokDay(calSta.getTime()) && WorkDayCaches.isRestToWorkDay(calSta.getTime())) {
				// 休息日且调整为工作日
				days++;
			}
			calSta.add(Calendar.DATE, 1);
		}
		return days;
	}

	/**
	 * 自然日耗时规则： <br>
	 * 当天: 上午开始 下午结束=0.5天 否则为0 <br>
	 * 隔天: 上午开始 {上午结束=1天 下午结束1.5天} 下午开始{上午结束=0.5天 下午结束1天}
	 * 
	 * @param lostDays
	 * @param startHour
	 * @param endHour
	 * @return
	 */
	private static Double fiexNatureLostDays(Double lostDays, int startHour, int endHour) {
		if (lostDays == 0) {
			// 当天
			if (startHour < WorkDayUtils.END_OF_AM && endHour > WorkDayUtils.END_OF_AM) {
				// 上午开始 下午结束
				lostDays = 0.5D;
			}
		} else if (lostDays > 0) {
			// 隔天
			if (startHour < WorkDayUtils.END_OF_AM && endHour > WorkDayUtils.END_OF_AM) {
				// 上午开始 下午结束
				lostDays += 0.5D;
			} else if (startHour > WorkDayUtils.END_OF_AM && endHour < WorkDayUtils.END_OF_AM) {
				// 下午开始 上午结束
				lostDays -= 0.5D;
			}
		}
		return lostDays;
	}

	private static Double fiexNatureRemainedDays(Double lostDays, int startHour, int endHour) {
		if (lostDays == 0) {
			// 当天
			if (startHour < WorkDayUtils.END_OF_AM && endHour > WorkDayUtils.END_OF_AM) {
				// 上午开始 下午结束
				lostDays = 0.5D;
			}
		} else if (lostDays > 0) {
			// 隔天
			if (startHour < WorkDayUtils.END_OF_AM && endHour > WorkDayUtils.END_OF_AM) {
				// 上午开始 下午结束
				lostDays += 0.5D;
			} else if (startHour > WorkDayUtils.END_OF_AM && endHour < WorkDayUtils.END_OF_AM) {
				// 下午开始 上午结束
				lostDays -= 0.5D;
			}
		}
		return lostDays;
	}

	/**
	 * 工作日耗时规则 <br>
	 * 当天(工作日数=1)：上午开始 下午结束 0.5 ,否则为0 <br>
	 * 隔天(工作日数>1)：{上午开始 上午结束 || 下午开始 下午结束 = -1天},{上午开始 下午结束 -0.5天},{下午开始 上午结束
	 * -1.5天}
	 * 
	 * @param lostDays
	 * @param startHour
	 * @param endHour
	 * @return
	 */
	private static Double fiexWorkDateLostDays(double lostDays, int startHour, int endHour) {
		if (lostDays == 1) {
			// 当天
			if (startHour < WorkDayUtils.END_OF_AM && endHour > WorkDayUtils.END_OF_AM) {
				// 上午开始 下午结束
				lostDays = -0.5D;
			} else {
				lostDays = 0D;
			}
		} else if (lostDays > 1) {
			lostDays -= 1D;
			// 隔天
			if (startHour < WorkDayUtils.END_OF_AM && endHour > WorkDayUtils.END_OF_AM) {
				// 上午开始 下午结束
				lostDays += 0.5D;
			} else if (startHour > WorkDayUtils.END_OF_AM && endHour < WorkDayUtils.END_OF_AM) {
				// 下午开始 上午结束
				lostDays -= 0.5D;
			}
		}
		return lostDays;
	}

	private static Double fiexWorkDateRemainedDays(double lostDays, int startHour, int endHour) {
		if (lostDays == 1) {
			// 当天
			if (startHour < WorkDayUtils.END_OF_AM && endHour > WorkDayUtils.END_OF_AM) {
				// 上午开始 下午结束
				lostDays = -0.5D;
			} else {
				lostDays = 0D;
			}
		} else if (lostDays > 1) {
			lostDays -= 1D;
			// 隔天
			if (startHour < WorkDayUtils.END_OF_AM && endHour > WorkDayUtils.END_OF_AM) {
				// 上午开始 下午结束
				lostDays += 0.5D;
			} else if (startHour > WorkDayUtils.END_OF_AM && endHour < WorkDayUtils.END_OF_AM) {
				// 下午开始 上午结束
				lostDays -= 0.5D;
			}
		}
		return lostDays;
	}

	private static int compareDate(Date startTime, Date endTime, String limitKind) {
		// 如果开始大于结束，交换开始解释
		if (!"自然日".equals(limitKind)) {
			Calendar cal1 = Calendar.getInstance();
			cal1.setTime(startTime);

			Calendar cal2 = Calendar.getInstance();
			cal2.setTime(endTime);

			return cal1.get(Calendar.YEAR) * 480 + cal1.get(Calendar.MONTH) * 40 + cal1.get(Calendar.DATE) - cal2.get(Calendar.YEAR) * 480 - cal2.get(Calendar.MONTH) * 40 - cal2.get(Calendar.DATE);
		} else {
			return startTime.compareTo(endTime);
		}
	}

	/**
	 * 计算耗时
	 * 
	 * @param startTime
	 * @param endTime
	 * @param limitKind
	 * @param includeStartDay
	 * @return
	 */
	public static Double calcLostDaysBetween(Date startTime, Date endTime, String limitKind, boolean includeStartDay, boolean half) {
		if (startTime == null || endTime == null)
			return null;
		boolean isNegativeNumber = compareDate(startTime, endTime, limitKind) > 0;
		// 如果开始大于结束，交换开始解释
		if (isNegativeNumber) {
			Date tmp = new Date(startTime.getTime());
			startTime = endTime;
			endTime = tmp;
		}

		Calendar cal = Calendar.getInstance();
		cal.setTime(startTime);
		int startHour = cal.get(Calendar.HOUR_OF_DAY);
		cal.setTime(endTime);
		int endHour = cal.get(Calendar.HOUR_OF_DAY);
		if ("自然日".equals(limitKind)) {
			Double lostDays = new Double(Math.abs(CommonUtils.dateDiff("day", startTime, endTime)));
			if (!includeStartDay) {
				// 不含当前
				if (lostDays > 0)
					lostDays = lostDays - 1;
				// 开始时间设置为0，表示上午开始
				startHour = 0;
			}
			if (half)
				lostDays = fiexNatureLostDays(lostDays, startHour, endHour);
			return (isNegativeNumber ? -1 : 1) * lostDays;
		} else {
			Double lostDays = new Double(WorkDayUtils.calcWorkDaysBetween(startTime, endTime));
			if (lostDays >= 1) {
				if (!includeStartDay) {
					// 不含开始，如果开始是工作日，扣去
					if (WorkDayCaches.isWorkDate(isNegativeNumber ? endTime : startTime)) {
						lostDays -= 1;
					}
					startHour = 0;
				}
				if (half)
					lostDays = fiexWorkDateLostDays(lostDays, startHour, endHour);
				return (isNegativeNumber ? -1 : 1) * lostDays;
			} else {
				return lostDays;
			}
		}
	}
}