package io.gonzo.middleware.service;

import io.gonzo.middleware.web.dto.AreaCodeDTO;
import io.gonzo.middleware.web.dto.BaseDTO;
import io.gonzo.middleware.web.dto.TransactionsDTO;
import io.gonzo.middleware.web.dto.TransactionsStoreDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static io.gonzo.middleware.utils.XmlUtils.getTagValue;
import static io.gonzo.middleware.utils.XmlUtils.resultCodeByException;

@Slf4j
@Service
@RequiredArgsConstructor
public class NationalStatisticsService {

    @Value("${app.key}")
    private String key;

    private final AreaCodeService areaCodeService;

    // [전국 조회] 부동산 거래 건수 조회
    public List<TransactionsDTO> getNumberOfTransactionsByNationwide(BaseDTO dto) {

        List<AreaCodeDTO> parentsList = areaCodeService.getAreaCodeToParents();

        String startMonth = dto.getStartDate();

        String endMonth = dto.getEndDate();

        return parentsList.stream().map(parents ->
                getNumberOfTransactions(
                        TransactionsStoreDTO.builder()
                                .startDate(startMonth)
                                .endDate(endMonth)
                                .isYear(dto.isYear())
                                .region(parents.getCode())
                                .build()
                ))
                .flatMap(Collection::parallelStream)
                .collect(Collectors.toList());
    }

    // 부동산 거래 건수 조회
    public List<TransactionsDTO> getNumberOfTransactions(TransactionsStoreDTO dto) {

        List<TransactionsDTO> result = new ArrayList<>();

        boolean isYear = dto.isYear();

        try {

            StringBuffer stringBuffer = new StringBuffer();

            String url = "http://openapi.reb.or.kr/OpenAPI_ToolInstallPackage/service/rest/RealEstateTradingSvc/";

//            url += isYear ? "getRealEstateTradingCountYear" : "getRealEstateTradingCount";

            switch (dto.getApiCode()) {
                case "RealEstateTradingCountYear":
                    url += "getRealEstateTradingCountYear";
                    break;
                case "RealEstateTradingCount":
                    url += "getRealEstateTradingCount";
                    break;
                default:
                    url += "";
                    break;
            }

            // todo : api 별로 파라미터 바꾸기
            stringBuffer.append(url)
                    .append("?ServiceKey=")
                    .append(key)
                    .append(getByStartDateParam(isYear, dto.getStartDate()))
                    .append(getByEndDateParam(isYear, dto.getEndDate()))
                    .append("&region=")
                    .append(dto.getRegion())
                    .append("&tradingtype=")
                    .append("01");

            String publicUrl = stringBuffer.toString();

            log.info("public url :: >> [ {} ]", publicUrl);

            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();

            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();

            Document doc = dBuilder.parse(publicUrl);

            doc.getDocumentElement().normalize();

            resultCodeByException(doc);

            NodeList nList = doc.getElementsByTagName("item");

            int itemSize = nList.getLength();

            for (int temp = 0; temp < itemSize; temp++) {

                Node nNode = nList.item(temp);

                if (nNode.getNodeType() == Node.ELEMENT_NODE) {

                    Element eElement = (Element) nNode;

                    String rsRow = getTagValue("rsRow", eElement);

                    String regionNm = getTagValue("regionNm", eElement);

                    List<TransactionsDTO> transactionsList = Arrays.stream(rsRow.split("\\|"))
                            .map(countData -> {

                                String[] arrayOfRsRow = countData.split(",");

                                String standardDate = passerByStandardDate(arrayOfRsRow[0], dto.isYear());

                                return TransactionsDTO.builder()
                                        .regionName(regionNm)
                                        .date(standardDate)
                                        .count(arrayOfRsRow[1])
                                        .build();
                            })
                            .collect(Collectors.toList());

                    result.addAll(transactionsList);
                }

            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }

    private String passerByStandardDate(String standardDate, boolean isYear) {

        if (isYear == Boolean.TRUE) {
            return standardDate;
        }

        int standardDateSize = standardDate.length();

        return standardDate.substring(0, 4) + "-" + standardDate.substring(4, standardDateSize);
    }

    private String getByStartDateParam(boolean isYear, String startDate) {
        return (isYear ? "&startyear=" : "&startmonth=") + startDate;
    }

    private String getByEndDateParam(boolean isYear, String endDate) {
        return (isYear ? "&endyear=" : "&endmonth=") + endDate;
    }

}
