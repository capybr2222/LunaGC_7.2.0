package emu.grasscutter.server.http.dispatch;

import static emu.grasscutter.config.Configuration.*;

import com.google.gson.*;
import com.google.protobuf.ByteString;
import emu.grasscutter.*;
import emu.grasscutter.Grasscutter.ServerRunMode;
import emu.grasscutter.net.proto.QueryCurrRegionHttpRspOuterClass.QueryCurrRegionHttpRsp;
import emu.grasscutter.net.proto.QueryRegionListHttpRspOuterClass.QueryRegionListHttpRsp;
import emu.grasscutter.net.proto.RegionInfoOuterClass.RegionInfo;
import emu.grasscutter.net.proto.RegionSimpleInfoOuterClass.RegionSimpleInfo;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.ResVersionConfigOuterClass.ResVersionConfig;
import emu.grasscutter.net.proto.StopServerInfoOuterClass.StopServerInfo;
import emu.grasscutter.server.event.dispatch.*;
import emu.grasscutter.server.http.Router;
import emu.grasscutter.server.http.objects.QueryCurRegionRspJson;
import emu.grasscutter.utils.*;
import io.javalin.Javalin;
import io.javalin.http.Context;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.slf4j.Logger;

public final class RegionHandler implements Router {
    private static final Map<String, RegionData> regions = new ConcurrentHashMap<>();
    private static String regionListResponse;
    private static String regionListResponseCN;
    private static com.google.protobuf.ByteString regionConfigEncrypted;
    private static com.google.protobuf.ByteString regionConfigEncryptedCN;
    private static final JsonObject CN_REL_ANDROID_7_2_0_CONFIG =
            JsonParser.parseString(
                            """
                            {
                              "RegionInfo": {
                                "ClientDataMd5": "{\\"remoteName\\": \\"data_versions\\", \\"md5\\": \\"2d53a763cf78415d4a90c81be3419da5\\", \\"hash\\": \\"698d32dbdde5bc46\\", \\"fileSize\\": 71416}\\r\\n{\\"remoteName\\": \\"data_versions_medium\\", \\"md5\\": \\"6bf505fc8e990e881ae5b436b6a4071c\\", \\"hash\\": \\"ff06d2df13f05f4e\\", \\"fileSize\\": 7002}",
                                "ResourceUrl": "https://autopatchcn.yuanshen.com/client_game_res/7.2_live",
                                "NextResourceUrl": "https://autopatchcn.yuanshen.com/client_game_res/7.2_live",
                                "ResourceUrlBak": "7.2_live",
                                "DataUrl": "https://autopatchcn.yuanshen.com/client_design_data/7.2_live",
                                "NextResVersionConfig": {
                                  "Md5": "0",
                                  "ReleaseTotalSize": "0",
                                  "Branch": "7.2_live"
                                },
                                "ResVersionConfig": {
                                  "Version": 49086080,
                                  "Md5": "{\\"remoteName\\": \\"res_versions_external\\", \\"md5\\": \\"c43ec70d6414cfacc791f6f5a6e6be61\\", \\"hash\\": \\"bd0b7c87ab57a2ad\\", \\"fileSize\\": 253188}\\r\\n{\\"remoteName\\": \\"res_versions_medium\\", \\"md5\\": \\"6d64df38efdea25286af0fb7fd6ea03d\\", \\"hash\\": \\"e41d855d997fd514\\", \\"fileSize\\": 13625}\\r\\n{\\"remoteName\\": \\"res_versions_streaming\\", \\"md5\\": \\"fee1dc3f5842b44ffa351deb468636af\\", \\"hash\\": \\"fcc78e712c48aa77\\", \\"fileSize\\": 1186}\\r\\n{\\"remoteName\\": \\"base_revision\\", \\"md5\\": \\"403247d78dc495d3bf57c7133d01978a\\", \\"hash\\": \\"cb86f266266031bf\\", \\"fileSize\\": 19}\\r\\n{\\"remoteName\\": \\"patch_node_versions\\", \\"md5\\": \\"9253fada5d5234e84665d79d90c9691e\\", \\"hash\\": \\"240857404f8b1cd4\\", \\"fileSize\\": 7544}",
                                  "ReleaseTotalSize": "0",
                                  "VersionSuffix": "1c55d912bf",
                                  "Branch": "7.2_live"
                                },
                                "GameBiz": "hk4e_cn",
                                "AreaType": "CN",
                                "ClientDataVersion": 49291486,
                                "ClientSilenceDataVersion": 49291486,
                                "ClientVersionSuffix": "c25ba650a6",
                                "ClientSilenceVersionSuffix": "c25ba650a6",
                                "UseGateserverDomainName": false,
                                "GateserverIp": "47.116.110.112",
                                "GateserverPort": 22101,
                                "PrivacyPolicyUrl": "https://user.mihoyo.com/#/about/privacyInGame?app_id=4&lang=zh-cn&biz=hk4e_cn",
                                "CdkeyUrl": "https://public-operation-hk4e.mihoyo.com/common/apicdkey/api/exchangeCdkey?sign_type=2&auth_appid=apicdkey&authkey_ver=1"
                              },
                              "Retcode": 0
                            }
                            """)
                    .getAsJsonObject();

    public RegionHandler() {
        try {
            this.initialize();
        } catch (Exception exception) {
            Grasscutter.getLogger().error("Failed to initialize region data.", exception);
        }
    }

    private static String getEffectiveGameAddress(Context ctx) {
        var configured = GAME_INFO.accessAddress;
        if (configured != null && !configured.isEmpty() && !configured.equals("0.0.0.0")) {
            return configured;
        }
        var host = ctx.host();
        if (host != null && !host.isEmpty()) {
            var h = host.contains(":") ? host.substring(0, host.lastIndexOf(':')) : host;
            if (!h.equals("127.0.0.1") && !h.equalsIgnoreCase("localhost") && !h.isEmpty()) {
                return h;
            }
        }
        return GAME_INFO.bindAddress;
    }

    private static String getEffectiveDispatchDomain(Context ctx) {
        var scheme = "http" + (HTTP_ENCRYPTION.useInRouting ? "s" : "");
        var configured = HTTP_INFO.accessAddress;
        if (configured != null && !configured.isEmpty() && !configured.equals("0.0.0.0")) {
            return scheme + "://" + configured + ":" + lr(HTTP_INFO.accessPort, HTTP_INFO.bindPort);
        }
        var host = ctx.host();
        if (host != null && !host.isEmpty()) {
            var h = host.contains(":") ? host.substring(0, host.lastIndexOf(':')) : host;
            if (!h.equals("127.0.0.1") && !h.equalsIgnoreCase("localhost") && !h.isEmpty()) {
                return scheme + "://" + h + ":" + ctx.port();
            }
        }
        return scheme + "://" + HTTP_INFO.bindAddress + ":" + lr(HTTP_INFO.accessPort, HTTP_INFO.bindPort);
    }

    private void initialize() {
        var configuredRegions = new ArrayList<>(DISPATCH_INFO.regions);
        if (Grasscutter.getRunMode() != ServerRunMode.HYBRID && configuredRegions.size() == 0) {
            Grasscutter.getLogger()
                    .error(
                            "[Dispatch] There are no game servers available. Exiting due to unplayable state.");
            System.exit(1);
        } else if (configuredRegions.size() == 0)
            configuredRegions.add(
                    new Region(
                            "os_usa",
                            DISPATCH_INFO.defaultName,
                            lr(GAME_INFO.accessAddress, GAME_INFO.bindAddress),
                            lr(GAME_INFO.accessPort, GAME_INFO.bindPort)));

        configuredRegions.forEach(
                region -> {
                    var regionInfo =
                            RegionInfo.newBuilder()
                                    .setGateserverIp(region.Ip)
                                    .setGateserverPort(region.Port)
                                    .build();
                    var updatedQuery =
                            QueryCurrRegionHttpRsp.newBuilder()
                                    .setRegionInfo(regionInfo)
                                    .setClientSecretKey(ByteString.copyFrom(Crypto.DISPATCH_SEED))
                                    .build();
                    regions.put(
                            region.Name,
                            new RegionData(
                                    updatedQuery, Utils.base64Encode(updatedQuery.toByteString().toByteArray())));
                });

        var hiddenIcons = new JsonArray();
        hiddenIcons.add(40);
        var codeSwitch = new JsonArray();
        codeSwitch.add(4334);

        var customConfig = new JsonObject();
        customConfig.addProperty("sdkenv", "2");
        customConfig.addProperty("checkdevice", "false");
        customConfig.addProperty("loadPatch", "false");
        customConfig.addProperty("showexception", String.valueOf(GameConstants.DEBUG));
        customConfig.addProperty("regionConfig", "pm|fk|add");
        customConfig.addProperty("downloadMode", "0");
        customConfig.add("codeSwitch", codeSwitch);
        customConfig.add("coverSwitch", hiddenIcons);

        var encodedConfig = JsonUtils.encode(customConfig).getBytes();
        Crypto.xor(encodedConfig, Crypto.DISPATCH_KEY);
        regionConfigEncrypted = ByteString.copyFrom(encodedConfig);

        customConfig.addProperty("sdkenv", "0");
        encodedConfig = JsonUtils.encode(customConfig).getBytes();
        Crypto.xor(encodedConfig, Crypto.DISPATCH_KEY);
        regionConfigEncryptedCN = ByteString.copyFrom(encodedConfig);
    }

    private static String buildRegionListResponse(Context ctx, boolean cn) {
        var dispatchDomain = getEffectiveDispatchDomain(ctx);
        var servers = new ArrayList<RegionSimpleInfo>();
        regions.keySet().stream()
                .sorted()
                .forEach(
                        name ->
                                servers.add(
                                        RegionSimpleInfo.newBuilder()
                                                .setName(name)
                                                .setTitle(name)
                                                .setType("DEV_PUBLIC")
                                                .setDispatchUrl(dispatchDomain + "/query_cur_region/" + name)
                                                .build()));
        var updatedRegionList =
                QueryRegionListHttpRsp.newBuilder()
                        .addAllRegionList(servers)
                        .setClientSecretKey(ByteString.copyFrom(Crypto.DISPATCH_SEED))
                        .setClientCustomConfigEncrypted(cn ? regionConfigEncryptedCN : regionConfigEncrypted)
                        .setEnableLoginPc(true)
                        .build();
        return Utils.base64Encode(updatedRegionList.toByteString().toByteArray());
    }

    private static boolean isCnAndroid72Version(String versionName) {
        return versionName != null && versionName.matches("CNRELAndroid7\\.2\\.0(?:$|[^0-9].*)");
    }

    private static QueryCurrRegionHttpRsp buildCnAndroid72RegionResponse(
            Context ctx, RegionData region) {
        var config = CN_REL_ANDROID_7_2_0_CONFIG.getAsJsonObject("RegionInfo");
        var resourceConfig = config.getAsJsonObject("ResVersionConfig");
        var nextResourceConfig = config.getAsJsonObject("NextResVersionConfig");

        var regionInfo =
                RegionInfo.newBuilder()
                        .setGateserverIp(getEffectiveGameAddress(ctx))
                        .setGateserverPort(
                                region == null
                                        ? lr(GAME_INFO.accessPort, GAME_INFO.bindPort)
                                        : region.getRegionQuery().getRegionInfo().getGateserverPort())
                        .setClientDataMd5(config.get("ClientDataMd5").getAsString())
                        .setResourceUrl(config.get("ResourceUrl").getAsString())
                        .setNextResourceUrl(config.get("NextResourceUrl").getAsString())
                        .setResourceUrlBak(config.get("ResourceUrlBak").getAsString())
                        .setDataUrl(config.get("DataUrl").getAsString())
                        .setResVersionConfig(
                                ResVersionConfig.newBuilder()
                                        .setVersion(resourceConfig.get("Version").getAsInt())
                                        .setMd5(resourceConfig.get("Md5").getAsString())
                                        .setReleaseTotalSize(
                                                resourceConfig.get("ReleaseTotalSize").getAsString())
                                        .setVersionSuffix(
                                                resourceConfig.get("VersionSuffix").getAsString())
                                        .setBranch(resourceConfig.get("Branch").getAsString())
                                        .build())
                        .setNextResVersionConfig(
                                ResVersionConfig.newBuilder()
                                        .setMd5(nextResourceConfig.get("Md5").getAsString())
                                        .setReleaseTotalSize(
                                                nextResourceConfig.get("ReleaseTotalSize").getAsString())
                                        .setBranch(nextResourceConfig.get("Branch").getAsString())
                                        .build())
                        .setGameBiz(config.get("GameBiz").getAsString())
                        .setAreaType(config.get("AreaType").getAsString())
                        .setClientDataVersion(config.get("ClientDataVersion").getAsInt())
                        .setClientSilenceDataVersion(
                                config.get("ClientSilenceDataVersion").getAsInt())
                        .setClientVersionSuffix(config.get("ClientVersionSuffix").getAsString())
                        .setClientSilenceVersionSuffix(
                                config.get("ClientSilenceVersionSuffix").getAsString())
                        .setUseGateserverDomainName(
                                config.get("UseGateserverDomainName").getAsBoolean())
                        .setPrivacyPolicyUrl(config.get("PrivacyPolicyUrl").getAsString())
                        .setCdkeyUrl(config.get("CdkeyUrl").getAsString())
                        .build();

        return QueryCurrRegionHttpRsp.newBuilder()
                .setRegionInfo(regionInfo)
                .setClientSecretKey(ByteString.copyFrom(Crypto.DISPATCH_SEED))
                .build();
    }

    @Override
    public void applyRoutes(Javalin javalin) {
        javalin.get("/query_region_list", RegionHandler::queryRegionList);
        javalin.get("/query_cur_region/{region}", RegionHandler::queryCurrentRegion);
        javalin.get("/query_server_address", RegionHandler::queryServerAddress);
    }

    private static void queryRegionList(Context ctx) {
        Logger logger = Grasscutter.getLogger();
        if (ctx.queryParamMap().containsKey("version") && ctx.queryParamMap().containsKey("platform")) {
            String versionName = ctx.queryParam("version");
            String versionCode = versionName.substring(0, 8);
            String platformName = ctx.queryParam("platform");

            if ("CNRELiOS".equals(versionCode)
                    || "CNRELWin".equals(versionCode)
                    || "CNRELAnd".equals(versionCode)) {
                QueryAllRegionsEvent event =
                        new QueryAllRegionsEvent(buildRegionListResponse(ctx, true));
                event.call();

                ctx.result(event.getRegionList());
            } else if ("OSRELiOS".equals(versionCode)
                    || "OSRELWin".equals(versionCode)
                    || "OSRELAnd".equals(versionCode)) {
                QueryAllRegionsEvent event =
                        new QueryAllRegionsEvent(buildRegionListResponse(ctx, false));
                event.call();

                ctx.result(event.getRegionList());
            } else {
                QueryAllRegionsEvent event =
                        new QueryAllRegionsEvent(buildRegionListResponse(ctx, false));
                event.call();

                ctx.result(event.getRegionList());
            }
        } else {
            QueryAllRegionsEvent event =
                    new QueryAllRegionsEvent(buildRegionListResponse(ctx, false));
            event.call();

            ctx.result(event.getRegionList());
        }
        Grasscutter.getLogger()
                .info(String.format("[Dispatch] Client %s request: query_region_list", Utils.address(ctx)));
    }

    private static void queryCurrentRegion(Context ctx) {
        String versionName = ctx.queryParam("version");

        if (!Grasscutter.getConfig().server.game.useXorEncryption) {
            if (isCnAndroid72Version(versionName)) {
                var hotfixResponse =
                        buildCnAndroid72RegionResponse(ctx, regions.get(ctx.pathParam("region")));
                var rsp = new QueryCurRegionRspJson();
                rsp.content = Utils.base64Encode(hotfixResponse.toByteArray());
                rsp.sign = "TW9yZSBsb3ZlIGZvciBVQSBQYXRjaCBwbGF5ZXJz";
                ctx.json(rsp);
                return;
            }
            ctx.result("{\"content\":\"CwpC8q8wBdR4rSauLQeF+PKeX+dwTUQt7hNupyqVBH19SdnOzx2WSDYAyC+XlrSZr0PluJ4waFCz9Y2YRl1HBzB0iCPhQ+/BtPGjJ22bU0CvmPXGxtocxd5p8SP59cnZSoEVtFYXeUk2EtVYVQdLXFtpPLSpCZdJzvYpGyN4wwOmLw4kqnCqCWKE8FB9dzevENgjFDSHBPpLexhgfiwDuQ408NUxD4E/Tz7966FCXUQ1zecorWhddSwTN4VCKpNvvRMCUAXbxlJXRK47B4FMLReexnHPSG6nf0tSYWqbMzQM6g+bsRq49tkS3tBvjZtssqHSyb2MK4MyHwMKf3xe5w==\",\"sign\":\"JPKMihNeHvXqm0NIGaz1TqmGZRH9sfaYQjflK9DiN3QhRNXWQ+Kh+uXmn/H/PnMQjtcFwpevmziUX7ajeZ2VH9MFM8Rqb7hjIHNIMecov9vSIBnxNTYbR9XDaG4gNDzkF7LCwvR3BKxz+llADosHKM3aakTJdZJJuVB2LNyuinBFNMcMV/95EmHP1PBG1boFjHeJd/SebB4MEkQlivZUuaQrBrteomouAtQYzy3Vp+aWIFxtdcACKYRm9QIIBZSD2SM8vTJXAE7MWyJPGHmGrzaCuPhqdhP0I5ACVgjQ6kyCAoyM27YudDmOAlqHxdAzmN25eimK1ltSf3GHzjuT6w==\"}");
        } else {
            String regionName = ctx.pathParam("region");
            var region = regions.get(regionName);

            String regionData = "CAESGE5vdCBGb3VuZCB2ZXJzaW9uIGNvbmZpZw==";
            if (isCnAndroid72Version(versionName)) {
                regionData =
                        Utils.base64Encode(
                                buildCnAndroid72RegionResponse(ctx, region).toByteArray());
            } else if (!ctx.queryParamMap().values().isEmpty()) {
                if (region != null) {
                    var effectiveQuery =
                            region.getRegionQuery().toBuilder()
                                    .setRegionInfo(
                                            RegionInfo.newBuilder()
                                                    .setGateserverIp(getEffectiveGameAddress(ctx))
                                                    .setGateserverPort(
                                                            region.getRegionQuery()
                                                                    .getRegionInfo()
                                                                    .getGateserverPort()))
                                    .build();
                    regionData = Utils.base64Encode(effectiveQuery.toByteString().toByteArray());
                }
            }

            var clientVersion = versionName.replaceAll(Pattern.compile("[a-zA-Z]").pattern(), "");
            var versionCode = clientVersion.split("\\.");
            var versionMajor = Integer.parseInt(versionCode[0]);
            var versionMinor = Integer.parseInt(versionCode[1]);
            var versionFix = Integer.parseInt(versionCode[2]);

            if (versionMajor >= 3
                || (versionMajor == 2 && versionMinor == 7 && versionFix >= 50)
                || (versionMajor == 2 && versionMinor == 8)) {
                try {
                    QueryCurrentRegionEvent event = new QueryCurrentRegionEvent(regionData);
                    event.call();

                    String key_id = ctx.queryParam("key_id");

                    if (versionMajor != GameConstants.VERSION_PARTS[0]
                        || versionMinor != GameConstants.VERSION_PARTS[1]
                    ) {

                        boolean updateClient = GameConstants.VERSION.compareTo(clientVersion) > 0;

                        QueryCurrRegionHttpRsp rsp =
                            QueryCurrRegionHttpRsp.newBuilder()
                                .setRetcode(Retcode.RET_STOP_SERVER_VALUE)
                                .setMsg("Connection Failed!")
                                .setRegionInfo(RegionInfo.newBuilder())
                                .setStopServer(
                                    StopServerInfo.newBuilder()
                                        .setUrl("https://discord.gg/T5vZU6UyeG")
                                        .setStopBeginTime((int) Instant.now().getEpochSecond())
                                        .setStopEndTime((int) Instant.now().getEpochSecond() + 1)
                                        .setContentMsg(
                                            updateClient
                                                ? "\nVersion mismatch outdated client! \n\nServer version: %s\nClient version: %s"
                                                .formatted(GameConstants.VERSION, clientVersion)
                                                : "\nVersion mismatch outdated server! \n\nServer version: %s\nClient version: %s"
                                                .formatted(GameConstants.VERSION, clientVersion))
                                        .build())
                                .buildPartial();

                        Grasscutter.getLogger()
                            .debug(
                                String.format(
                                    "Connection denied for %s due to %s.",
                                    Utils.address(ctx), updateClient ? "outdated client!" : "outdated server!"));

                        ctx.json(Crypto.encryptAndSignRegionData(rsp.toByteArray(), key_id));
                        return;
                    }

                    if (ctx.queryParam("dispatchSeed") == null) {
                        var rsp = new QueryCurRegionRspJson();

                        rsp.content = event.getRegionInfo();
                        rsp.sign = "TW9yZSBsb3ZlIGZvciBVQSBQYXRjaCBwbGF5ZXJz";

                        ctx.json(rsp);
                        return;
                    }

                    var regionInfo = Utils.base64Decode(event.getRegionInfo());

                    ctx.json(Crypto.encryptAndSignRegionData(regionInfo, key_id));
                } catch (Exception e) {
                    Grasscutter.getLogger().error("An error occurred while handling query_cur_region.", e);
                }
            } else {
                QueryCurrentRegionEvent event = new QueryCurrentRegionEvent(regionData);
                event.call();
                ctx.result(event.getRegionInfo());
            }
            Grasscutter.getLogger()
                .info(
                    String.format(
                        "Client %s request: query_cur_region/%s", Utils.address(ctx), regionName));
        }
    }

    public static void queryServerAddress(Context ctx) {
        var addr = Grasscutter.getConfig().server.game.accessAddress;
        var port = Grasscutter.getConfig().server.game.accessPort == 0 ?
            Grasscutter.getConfig().server.game.bindPort :
            Grasscutter.getConfig().server.game.accessPort;
        ctx.result(addr + ":" + port);
    }

    public static class RegionData {
        private final QueryCurrRegionHttpRsp regionQuery;
        private final String base64;

        public RegionData(QueryCurrRegionHttpRsp prq, String b64) {
            this.regionQuery = prq;
            this.base64 = b64;
        }

        public QueryCurrRegionHttpRsp getRegionQuery() {
            return this.regionQuery;
        }

        public String getBase64() {
            return this.base64;
        }
    }

    public static QueryCurrRegionHttpRsp getCurrentRegion() {
        return Grasscutter.getRunMode() == ServerRunMode.HYBRID
                ? regions.get("os_usa").getRegionQuery()
                : null;
    }
}
