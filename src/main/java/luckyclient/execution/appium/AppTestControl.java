package luckyclient.execution.appium;

import java.io.IOException;
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

/**
 * =================================================================
 * ����һ�������Ƶ�������������������κ�δ�������ǰ���¶Գ����������޸ĺ�������ҵ��;��Ҳ������Գ�������޸ĺ����κ���ʽ�κ�Ŀ�ĵ��ٷ�����
 * Ϊ���������ߵ��Ͷ��ɹ���LuckyFrame�ؼ���Ȩ��Ϣ�Ͻ��۸� ���κ����ʻ�ӭ��ϵ�������ۡ� QQ:1573584944 seagull1985
 * =================================================================
 * 
 * @author�� seagull
 * 
 * @date 2017��12��1�� ����9:29:40
 * 
 */
public class AppTestControl {

	/**
	 * ����̨ģʽ���ȼƻ�ִ������
	 * @param planname ���Լƻ�����
	 */
	public static void manualExecutionPlan(String planname) {
		// ������־�����ݿ�
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
			LogUtil.APP.error("����̨ģʽ��ʼ��Appium Driver�쳣��", e);
		}
		serverOperation caselog = new serverOperation();
		List<ProjectCase> testCases = GetServerApi.getCasesbyplanname(planname);
		List<ProjectCaseParams> pcplist = new ArrayList<>();
		if (testCases.size() != 0) {
			pcplist = GetServerApi.cgetParamsByProjectid(String.valueOf(testCases.get(0).getProjectId()));
		}
		LogUtil.APP.info("��ǰ�ƻ��ж�ȡ��������{}��",testCases.size());
		int i = 0;
		for (ProjectCase testcase : testCases) {
			List<ProjectCaseSteps> steps = GetServerApi.getStepsbycaseid(testcase.getCaseId());
			if (steps.size() == 0) {
				continue;
			}
			i++;
			LogUtil.APP.info("��ʼִ�мƻ��еĵ�{}����������{}��......",i,testcase.getCaseSign());
			try {
				if ("Android".equals(properties.getProperty("platformName"))) {
					AndroidCaseExecution.caseExcution(testcase, steps, taskid, androiddriver, caselog, pcplist);
				} else if ("IOS".equals(properties.getProperty("platformName"))) {
					IosCaseExecution.caseExcution(testcase, steps, taskid, iosdriver, caselog, pcplist);
				}
			} catch (Exception e) {
				LogUtil.APP.error("�û�ִ�й������׳�Exception�쳣��", e);
			}
			LogUtil.APP.info("��ǰ��������{}��ִ�����......������һ��",testcase.getCaseSign());
		}
		LogUtil.APP.info("��ǰ��Ŀ���Լƻ��е������Ѿ�ȫ��ִ�����...");
		// �ر�APP�Լ�appium�Ự
		if ("Android".equals(properties.getProperty("platformName"))) {
			assert androiddriver != null;
			androiddriver.closeApp();
		} else if ("IOS".equals(properties.getProperty("platformName"))) {
			assert iosdriver != null;
			iosdriver.closeApp();
		}
	}

	public static void taskExecutionPlan(TaskExecute task) throws InterruptedException {
		System.out.println("APP AutoTest Start!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
		// ��¼��־�����ݿ�
		String taskId=task.getTaskId().toString();
		serverOperation.exetype = 0;
		TestControl.TASKID = taskId;
		AndroidDriver<AndroidElement> androiddriver = null;
		IOSDriver<IOSElement> iosdriver = null;
		Properties properties = AppiumConfig.getConfiguration();
		AppiumService as=null;
		//���������Զ�����Appiume����
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
		// �ж��Ƿ�Ҫ�Զ�����TOMCAT
		if (restartstatus.contains("Status:true")) {
			// �ж��Ƿ񹹽��Ƿ�ɹ�
			if (BuildResult.SUCCESS.equals(buildResult)) {
				try {
					if ("Android".equals(properties.getProperty("platformName"))) {
//						//LD add��׿¼����ʼ
//						try {
//							Runtime.getRuntime().exec("adb shell \"screenrecord /sdcard/crash/And"+RecFileName+"&echo $! >/sdcard/crash/pid.txt\"");
//							System.out.println("��׿¼����ʼ��������������������������������"+"name="+RecFileName);
//						} catch (IOException e) {
//							e.printStackTrace();
//						}

						androiddriver = AppiumInitialization.setAndroidAppium(properties);
						//LD add�л���webView����H5ҳ���Զ���ִ��
						androiddriver.context("WEBVIEW_com.lwljuyang.mobile.juyang");
						System.out.println("�л�context��ɣ�����������������������������������������������������������");
						LogUtil.APP.info("���AndroidDriver��ʼ������...APPIUM Server��http://{}/wd/hub��",properties.getProperty("appiumsever"));

					} else if ("IOS".equals(properties.getProperty("platformName"))) {
						iosdriver = AppiumInitialization.setIosAppium(properties);
						LogUtil.APP.info("���IOSDriver��ʼ������...APPIUM Server��http://{}/wd/hub��",properties.getProperty("appiumsever"));
					}
				} catch (Exception e) {
					LogUtil.APP.error("��ʼ��AppiumDriver���� ��APPIUM Server��http://{}/wd/hub��",properties.getProperty("appiumsever"), e);
				}
				serverOperation caselog = new serverOperation();
				List<ProjectCase> cases = GetServerApi.getCasesbyplanId(taskScheduling.getPlanId());
				LogUtil.APP.info("��ǰ�ƻ���{}���й��С�{}��������������...",task.getTaskName(),cases.size());
				serverOperation.updateTaskExecuteStatusIng(taskId, cases.size());
				int i = 0;
				for (ProjectCase testcase : cases) {
					SimpleDateFormat dateformat = new SimpleDateFormat("yyyy-MM-ddHH:mm:ss");
					String dateStr = dateformat.format(System.currentTimeMillis());
                    String RecFileName=testcase.getCaseName()+System.currentTimeMillis()/1000+".mp4";
					//LD add��׿¼����ʼcase
					try {
						System.out.println("��׿¼����ʼ��������������������������������"+"RecFileName="+RecFileName);
						Runtime.getRuntime().exec("adb shell \"screenrecord /sdcard/crash/And"+RecFileName+"&echo $! >/sdcard/crash/pid.txt\"").waitFor(20, TimeUnit.SECONDS);
					} catch (IOException e) {
						e.printStackTrace();
					}
					i++;
					LogUtil.APP.info("��ʼִ�е�ǰ�������� {} �ĵڡ�{}������������:��{}��......",task.getTaskName(),i,testcase.getCaseSign());
					List<ProjectCaseSteps> steps = GetServerApi.getStepsbycaseid(testcase.getCaseId());
					if (steps.size() == 0) {
						continue;
					}
					try {
						//���뿪ʼִ�е�����
						caselog.insertTaskCaseExecute(taskId, taskScheduling.getProjectId(),testcase.getCaseId(),testcase.getCaseSign(), testcase.getCaseName(), 4);
						if ("Android".equals(properties.getProperty("platformName"))) {
							System.out.println("***************��ʼִ�а�׿����");
							AndroidCaseExecution.caseExcution(testcase, steps, taskId, androiddriver, caselog, pcplist);
						} else if ("IOS".equals(properties.getProperty("platformName"))) {
							IosCaseExecution.caseExcution(testcase, steps, taskId, iosdriver, caselog, pcplist);
						}
					} catch (Exception e) {
						LogUtil.APP.error("�û�ִ�й������׳��쳣��", e);
					}
					LogUtil.APP.info("��ǰ��������{}��ִ�����......������һ��",testcase.getCaseSign());
					//LD add��׿¼������case
					try {
						Runtime.getRuntime().exec("adb shell kill -SIGINT $(cat /sdcard/crash/pid.txt)").waitFor(10,TimeUnit.SECONDS);
						Thread.sleep(1000);
						Runtime.getRuntime().exec("adb pull /sdcard/crash/And"+RecFileName+" E:\\record\\And"+RecFileName).waitFor(10,TimeUnit.SECONDS);
						System.out.println("��׿¼��������������������������������������"+"RecFileName="+RecFileName);
					} catch (IOException e) {
						e.printStackTrace();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
				tastcount = serverOperation.updateTaskExecuteData(taskId, cases.size(),2);
				String testtime = serverOperation.getTestTime(taskId);
				LogUtil.APP.info("��ǰ��Ŀ��{}�����Լƻ��е������Ѿ�ȫ��ִ�����...",projectname);
				MailSendInitialization.sendMailInitialization(HtmlMail.htmlSubjectFormat(jobname),
						HtmlMail.htmlContentFormat(tastcount, taskId, buildResult.toString(), restartstatus, testtime, jobname),
						taskId, taskScheduling, tastcount,testtime,buildResult.toString(),restartstatus);

				// �ر�APP�Լ�appium�Ự
				if ("Android".equals(properties.getProperty("platformName"))) {
					assert androiddriver != null;
					androiddriver.closeApp();
				} else if ("IOS".equals(properties.getProperty("platformName"))) {
					assert iosdriver != null;
					iosdriver.closeApp();
				}
			} else {
				LogUtil.APP.warn("��Ŀ����ʧ�ܣ��Զ��������Զ��˳�����ǰ��JENKINS�м����Ŀ���������");
				MailSendInitialization.sendMailInitialization(jobname, "������Ŀ������ʧ�ܣ��Զ��������Զ��˳�����ǰȥJENKINS�鿴���������", taskId, taskScheduling, null,"0Сʱ0��0��",buildResult.toString(),restartstatus);
			}
		} else {
			LogUtil.APP.warn("��ĿTOMCAT����ʧ�ܣ��Զ��������Զ��˳���������ĿTOMCAT���������");
			MailSendInitialization.sendMailInitialization(jobname, "��ĿTOMCAT����ʧ�ܣ��Զ��������Զ��˳���������ĿTOMCAT���������", taskId, taskScheduling, null,"0Сʱ0��0��",buildResult.toString(),restartstatus);
		}
		//�ر�Appium������߳�
		if(as!=null){
			as.interrupt();
		}
	}

}
