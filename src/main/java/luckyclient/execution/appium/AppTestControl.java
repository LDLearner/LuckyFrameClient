package luckyclient.execution.appium;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import com.offbytwo.jenkins.model.BuildResult;

import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.AndroidElement;
import io.appium.java_client.ios.IOSDriver;
import io.appium.java_client.ios.IOSElement;
import luckyclient.execution.appium.androidex.AndroidCaseExecution;
import luckyclient.execution.appium.iosex.IosCaseExecution;
import luckyclient.execution.httpinterface.TestControl;
import luckyclient.remote.api.GetServerApi;
import luckyclient.remote.api.serverOperation;
import luckyclient.remote.entity.ProjectCase;
import luckyclient.remote.entity.ProjectCaseParams;
import luckyclient.remote.entity.ProjectCaseSteps;
import luckyclient.remote.entity.TaskExecute;
import luckyclient.remote.entity.TaskScheduling;
import luckyclient.tool.jenkins.BuildingInitialization;
import luckyclient.tool.mail.HtmlMail;
import luckyclient.tool.mail.MailSendInitialization;
import luckyclient.tool.shell.RestartServerInitialization;
import luckyclient.utils.LogUtil;
import luckyclient.utils.config.AppiumConfig;
import sun.font.TrueTypeFont;

/**
 * =================================================================
 * 这是一个受限制的自由软件！您不能在任何未经允许的前提下对程序代码进行修改和用于商业用途；也不允许对程序代码修改后以任何形式任何目的的再发布。
 * 为了尊重作者的劳动成果，LuckyFrame关键版权信息严禁篡改 有任何疑问欢迎联系作者讨论。 QQ:1573584944 seagull1985
 * =================================================================
 * 
 * @author： seagull
 * 
 * @date 2017年12月1日 上午9:29:40
 * 
 */
public class AppTestControl {

	/**
	 * 控制台模式调度计划执行用例
	 * @param planname 测试计划名称
	 */
	public static void manualExecutionPlan(String planname) {
		// 不记日志到数据库
		serverOperation.exetype = 1;
		String taskid = "888888";
		AndroidDriver<AndroidElement> androiddriver = null;
		IOSDriver<IOSElement> iosdriver = null;
		Properties properties = AppiumConfig.getConfiguration();
		try {
			if ("Android".equals(properties.getProperty("platformName"))) {
				androiddriver = AppiumInitialization.setAndroidAppium(properties);
			} else if ("IOS".equals(properties.getProperty("platformName"))) {
				iosdriver = AppiumInitialization.setIosAppium(properties);
			}

		} catch (IOException e) {
			// TODO Auto-generated catch block
			LogUtil.APP.error("控制台模式初始化Appium Driver异常！", e);
		}
		serverOperation caselog = new serverOperation();
		List<ProjectCase> testCases = GetServerApi.getCasesbyplanname(planname);
		List<ProjectCaseParams> pcplist = new ArrayList<>();
		if (testCases.size() != 0) {
			pcplist = GetServerApi.cgetParamsByProjectid(String.valueOf(testCases.get(0).getProjectId()));
		}
		LogUtil.APP.info("当前计划中读取到用例共{}个",testCases.size());
		int i = 0;
		for (ProjectCase testcase : testCases) {
			List<ProjectCaseSteps> steps = GetServerApi.getStepsbycaseid(testcase.getCaseId());
			if (steps.size() == 0) {
				continue;
			}
			i++;
			LogUtil.APP.info("开始执行计划中的第{}条用例：【{}】......",i,testcase.getCaseSign());
			try {
				if ("Android".equals(properties.getProperty("platformName"))) {
					AndroidCaseExecution.caseExcution(testcase, steps, taskid, androiddriver, caselog, pcplist);
				} else if ("IOS".equals(properties.getProperty("platformName"))) {
					IosCaseExecution.caseExcution(testcase, steps, taskid, iosdriver, caselog, pcplist);
				}
			} catch (Exception e) {
				LogUtil.APP.error("用户执行过程中抛出Exception异常！", e);
			}
			LogUtil.APP.info("当前用例：【{}】执行完成......进入下一条",testcase.getCaseSign());
		}
		LogUtil.APP.info("当前项目测试计划中的用例已经全部执行完成...");
		// 关闭APP以及appium会话
		if ("Android".equals(properties.getProperty("platformName"))) {
			assert androiddriver != null;
			androiddriver.closeApp();
		} else if ("IOS".equals(properties.getProperty("platformName"))) {
			assert iosdriver != null;
			iosdriver.closeApp();
		}
	}
	//LD add 字符输出函数
	private static void printMessage (final InputStream input){

		new Thread(new Runnable() {
			public void run() {
				Reader reader = new InputStreamReader(input);
				BufferedReader bf = new BufferedReader(reader);
				String line = null;
				try {
					while((line=bf.readLine())!=null) {
						System.out.println(line);
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}).start();
	}


	public static void taskExecutionPlan(TaskExecute task) throws InterruptedException {
		System.out.println("APP AutoTest Start!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
		// 记录日志到数据库
		String taskId=task.getTaskId().toString();
		serverOperation.exetype = 0;
		TestControl.TASKID = taskId;
		AndroidDriver<AndroidElement> androiddriver = null;
		IOSDriver<IOSElement> iosdriver = null;
		Properties properties = AppiumConfig.getConfiguration();
		AppiumService as=null;
		//根据配置自动启动Appiume服务
		if(Boolean.parseBoolean(properties.getProperty("autoRunAppiumService"))){
			as =new AppiumService();
			as.start();
			Thread.sleep(10000);
		}
		TaskScheduling taskScheduling = GetServerApi.cGetTaskSchedulingByTaskId(task.getTaskId());
		String restartstatus = RestartServerInitialization.restartServerRun(taskId);
		BuildResult buildResult = BuildingInitialization.buildingRun(taskId);
		List<ProjectCaseParams> pcplist = GetServerApi
				.cgetParamsByProjectid(task.getProjectId().toString());
		String projectname = task.getProject().getProjectName();
		String jobname = GetServerApi.cGetTaskSchedulingByTaskId(task.getTaskId()).getSchedulingName();
        int[] tastcount;
		// 判断是否要自动重启TOMCAT
		if (restartstatus.contains("Status:true")) {
			// 判断是否构建是否成功
			if (BuildResult.SUCCESS.equals(buildResult)) {
				try {
					if ("Android".equals(properties.getProperty("platformName"))) {
//						//LD add安卓录屏开始
//						try {
//							Runtime.getRuntime().exec("adb shell \"screenrecord /sdcard/crash/And"+RecFileName+"&echo $! >/sdcard/crash/pid.txt\"");
//							System.out.println("安卓录屏开始！！！！！！！！！！！！！！！！"+"name="+RecFileName);
//						} catch (IOException e) {
//							e.printStackTrace();
//						}

						androiddriver = AppiumInitialization.setAndroidAppium(properties);
						//LD add切换到webView用于H5页面自动化执行
						androiddriver.context("WEBVIEW_com.lwljuyang.mobile.juyang");
						System.out.println("切换context完成！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！");
						LogUtil.APP.info("完成AndroidDriver初始化动作...APPIUM Server【http://{}/wd/hub】",properties.getProperty("appiumsever"));

					} else if ("IOS".equals(properties.getProperty("platformName"))) {
						iosdriver = AppiumInitialization.setIosAppium(properties);
						LogUtil.APP.info("完成IOSDriver初始化动作...APPIUM Server【http://{}/wd/hub】",properties.getProperty("appiumsever"));
					}
				} catch (Exception e) {
					LogUtil.APP.error("初始化AppiumDriver出错 ！APPIUM Server【http://{}/wd/hub】",properties.getProperty("appiumsever"), e);
				}
				serverOperation caselog = new serverOperation();
				List<ProjectCase> cases = GetServerApi.getCasesbyplanId(taskScheduling.getPlanId());
				LogUtil.APP.info("当前计划【{}】中共有【{}】条待测试用例...",task.getTaskName(),cases.size());
				serverOperation.updateTaskExecuteStatusIng(taskId, cases.size());
				int i = 0;

				for (ProjectCase testcase : cases) {
					SimpleDateFormat dateformat = new SimpleDateFormat("yyyy-MM-ddHH:mm:ss");
					String dateStr = dateformat.format(System.currentTimeMillis());
                    String RecFileName=testcase.getCaseName()+System.currentTimeMillis()/1000+".mp4";
					//LD add安卓录屏开始case
					try {
						Process p = null;
						int success=0;
						do {
							System.out.println("安卓录屏开始！！！！！！！！！！！！！！！！" + "RecFileName=" + RecFileName);
							p = Runtime.getRuntime().exec("adb shell \"screenrecord --time-limit 20 --verbose /sdcard/crash/And" + RecFileName + "\"&echo $! >/sdcard/crash/pid.txt\"");
							//printMessage(p.getInputStream());
							//printMessage(p.getErrorStream());
							//Thread.sleep(1000);

							//Runtime.getRuntime().exec("adb shell \"screenrecord --time-limit 10 --verbose /sdcard/crash/And"+RecFileName+"\"&echo $! >/sdcard/crash/pid.txt\"").waitFor(10, TimeUnit.SECONDS);
							//Runtime.getRuntime().exec("adb shell \"nohup screenrecord --time-limit 10 --verbose /sdcard/crash/And"+RecFileName+"&echo $! >/sdcard/crash/pid.txt\"").waitFor(10, TimeUnit.SECONDS);
							//Thread.sleep(8000);
							Reader reader = new InputStreamReader(p.getInputStream());
							BufferedReader bf = new BufferedReader(reader);
							String line = null;
							try {
								if ((line = bf.readLine()) != null) {
									success=1;
									System.out.println(line);
								}
							} catch (IOException e) {
								e.printStackTrace();
							}
						}while (success==0);
					} catch (IOException e) {
						e.printStackTrace();
					}
					i++;
					LogUtil.APP.info("开始执行当前测试任务 {} 的第【{}】条测试用例:【{}】......",task.getTaskName(),i,testcase.getCaseSign());
					List<ProjectCaseSteps> steps = GetServerApi.getStepsbycaseid(testcase.getCaseId());
					if (steps.size() == 0) {
						continue;
					}
					try {
						//插入开始执行的用例
						caselog.insertTaskCaseExecute(taskId, taskScheduling.getProjectId(),testcase.getCaseId(),testcase.getCaseSign(), testcase.getCaseName(), 4);
						if ("Android".equals(properties.getProperty("platformName"))) {
							System.out.println("***************开始执行安卓用例");
							AndroidCaseExecution.caseExcution(testcase, steps, taskId, androiddriver, caselog, pcplist);
						} else if ("IOS".equals(properties.getProperty("platformName"))) {
							IosCaseExecution.caseExcution(testcase, steps, taskId, iosdriver, caselog, pcplist);
						}
					} catch (Exception e) {
						LogUtil.APP.error("用户执行过程中抛出异常！", e);
					}
					LogUtil.APP.info("当前用例：【{}】执行完成......进入下一条",testcase.getCaseSign());
					//LD add安卓录屏结束case
					try {
						//int value = p.waitFor();
						//System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!The Value="+value);
						Thread.sleep(2000);
						Runtime.getRuntime().exec("adb shell kill -SIGINT $(cat /sdcard/crash/pid.txt)").waitFor(10,TimeUnit.SECONDS);
						Thread.sleep(1000);
						System.out.println("安卓录屏结束！！！！！！！！！！！！！！！！"+"RecFileName="+RecFileName);
						Runtime.getRuntime().exec("adb pull /sdcard/crash/And"+RecFileName+" E:\\record\\And"+RecFileName).waitFor(10,TimeUnit.SECONDS);
						//Thread.sleep(10000);
					} catch (IOException e) {
						e.printStackTrace();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
				tastcount = serverOperation.updateTaskExecuteData(taskId, cases.size(),2);
				String testtime = serverOperation.getTestTime(taskId);
				LogUtil.APP.info("当前项目【{}】测试计划中的用例已经全部执行完成...",projectname);
				MailSendInitialization.sendMailInitialization(HtmlMail.htmlSubjectFormat(jobname),
						HtmlMail.htmlContentFormat(tastcount, taskId, buildResult.toString(), restartstatus, testtime, jobname),
						taskId, taskScheduling, tastcount,testtime,buildResult.toString(),restartstatus);

				// 关闭APP以及appium会话
				if ("Android".equals(properties.getProperty("platformName"))) {
					assert androiddriver != null;
					androiddriver.closeApp();
				} else if ("IOS".equals(properties.getProperty("platformName"))) {
					assert iosdriver != null;
					iosdriver.closeApp();
				}
			} else {
				LogUtil.APP.warn("项目构建失败，自动化测试自动退出！请前往JENKINS中检查项目构建情况。");
				MailSendInitialization.sendMailInitialization(jobname, "构建项目过程中失败，自动化测试自动退出！请前去JENKINS查看构建情况！", taskId, taskScheduling, null,"0小时0分0秒",buildResult.toString(),restartstatus);
			}
		} else {
			LogUtil.APP.warn("项目TOMCAT重启失败，自动化测试自动退出！请检查项目TOMCAT运行情况。");
			MailSendInitialization.sendMailInitialization(jobname, "项目TOMCAT重启失败，自动化测试自动退出！请检查项目TOMCAT运行情况！", taskId, taskScheduling, null,"0小时0分0秒",buildResult.toString(),restartstatus);
		}
		//关闭Appium服务的线程
		if(as!=null){
			as.interrupt();
		}
	}

}
