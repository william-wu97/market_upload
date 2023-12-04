package org.jeme;

import com.google.gson.Gson;
import org.jeme.config.Config;
import org.jeme.market.HuaWeiMarket;
import org.jeme.market.MiMarket;
import org.jeme.market.VIVOMarket;
import org.jeme.market.OPPOMarket;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;

public class MainProcess {

    private final LinkedHashMap<String, String> platformMap = new LinkedHashMap<>();

    /***
     * 支持的平台列表
     */
    public MainProcess() {
        platformMap.put("1", "全部");
        platformMap.put("2", "小米(mi)");
        platformMap.put("3", "VIVO");
        platformMap.put("4", "华为（HuaWei）");
        platformMap.put("5", "OPPO");
        platformMap.put("exit", "退出");
    }

    public void control() {
        String action;
        System.out.println("请选择需要执行的操作");
        System.out.println("1.上传更新apk");
        System.out.println("2.查看应用市场上架状态");
        System.out.println("exit.退出");
        action = new Scanner(System.in).nextLine();
        if ("exit".equals(action)) {
            return;
        }
        System.out.println(action.equals("1") ? "请选择需要上传的平台" : "请选择需要查询的平台");
        System.out.println("直接回车表示全部");
        String inputContent;
        boolean canContinue;
        do {
            System.out.println("*********");
            for (Map.Entry<String, String> entry : platformMap.entrySet()) {
                System.out.printf("%s.%s%n", entry.getKey(), entry.getValue());
            }
            System.out.println("*********");
            inputContent = new Scanner(System.in).nextLine();
            if (Utils.isEmpty(inputContent)) {
                System.out.println(action.equals("1") ? "默认上传所有平台" : "默认查询所有平台");
                inputContent = "1";
            }
            if ("exit".equals(inputContent)) {
                return;
            }

            if (!platformMap.containsKey(inputContent)) {
                System.out.println("请输入正确的编号");
                canContinue = false;

            } else {
                canContinue = true;
            }

        } while (!canContinue);
        System.out.println("您选择了" + platformMap.get(inputContent));
        Config config = getConfigFromLocal();
        if (config == null) {
            System.out.println("配置文件有错，请先检查！");
            return;
        }
        switchPlatform(inputContent, config, action);
    }


    private Config getConfigFromLocal() {
        String configJson = Utils.readFile(new File(Utils.getPath() + "/config.json"));
        if (Utils.isEmpty(configJson)) {
            return null;
        }
        return new Gson().fromJson(configJson, Config.class);
    }

    private void switchPlatform(String platformIndex, Config config, String action) {
        if (config == null || config.pushInfo == null || config.market == null) {
            return;
        }
        switch (platformIndex) {
            case "1":
                MarketFactory.getInstance().add(new MiMarket(config.market.get("mi"), config.pushInfo));
                MarketFactory.getInstance().add(new VIVOMarket(config.market.get("vivo"), config.pushInfo));
                MarketFactory.getInstance().add(new HuaWeiMarket(config.market.get("hw"), config.pushInfo));
                MarketFactory.getInstance().add(new OPPOMarket(config.market.get("oppo"), config.pushInfo));
                break;
            case "2":
                MarketFactory.getInstance().add(new MiMarket(config.market.get("mi"), config.pushInfo));
                break;
            case "3":
                MarketFactory.getInstance().add(new VIVOMarket(config.market.get("vivo"), config.pushInfo));
                break;
            case "4":
                MarketFactory.getInstance().add(new HuaWeiMarket(config.market.get("hw"), config.pushInfo));
                break;
            case "5":
                MarketFactory.getInstance().add(new OPPOMarket(config.market.get("oppo"), config.pushInfo));
                break;
            default:
                System.out.println("您输入了错误的编号");
                break;
        }
        if (action.equals("2")) {
            MarketFactory.getInstance().catMarket();
            return;
        }
        MarketFactory.getInstance().process();
    }
}
