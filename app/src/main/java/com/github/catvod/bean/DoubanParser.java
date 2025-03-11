package com.github.catvod.bean;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.github.catvod.net.OkHttp;

public class DoubanParser {

    public static DoubanInfo getDoubanInfo(String id, DoubanInfo info) {
        if (id == null || id.isEmpty()) {
            return info;
        }

        try {
            String url = "https://movie.douban.com/subject/" + id + "/";
            Document doc = Jsoup.parse(OkHttp.string(url));

            // 解析剧情简介
            String plot = doc.select("#link-report-intra span[property=v:summary]").text().trim();

            // 解析年份
            Element yearElement = doc.selectFirst(".year");
            String year = yearElement != null ? yearElement.text().replaceAll("[()]", "") : "";

            // 解析国家地区
            String region = parseRegion(doc.html());

            // 解析演员列表
            Elements actorElements = doc.select("meta[property^=video:actor]");
            List<String> actorList = new ArrayList<>();
            for (Element el : actorElements) {
                actorList.add(el.attr("content"));
            }
            String actors = String.join("/", actorList);

            // 解析导演
            Element directorElement = doc.selectFirst("meta[property=video:director]");
            String director = directorElement != null ? directorElement.attr("content") : "";

            // 解析类型
            Element genreElement = doc.selectFirst("span.pl:containsOwn(类型:)");
            Elements typeElements = genreElement != null ? genreElement.parent().select("span[property=v:genre]")
                    : new Elements();
            List<String> typeList = new ArrayList<>();
            for (Element el : typeElements) {
                typeList.add(el.text());
            }
            String type = String.join("/", typeList);

            // 解析评分
            Element ratingElement = doc.selectFirst("strong.ll.rating_num[property=v:average]");
            String rating = ratingElement != null ? ratingElement.text().trim() : "";

            // 构建结果对象
            info.setPlot(plot);
            info.setYear(year);
            info.setRegion(region);
            info.setActors(actors);
            info.setDirector(director);
            info.setType(type);
            info.setRating(rating);

            return info;
        } catch (Exception e) {
            return new DoubanInfo();
        }
    }

    private static String parseRegion(String html) {
        Pattern pattern = Pattern.compile("<span class=\"pl\">制片国家/地区:</span>[\\s\\n]*([^<]+)");
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "";
    }
}
