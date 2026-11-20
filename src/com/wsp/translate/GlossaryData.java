package com.wsp.translate;

/** 内置外贸/聊天常用术语速查数据（仅本机查阅，不参与翻译请求）。 */
public final class GlossaryData {

    private GlossaryData() { }

    /** 条目：缩写/术语、英文全称、中文说明。 */
    public static final String[][] ITEMS = {
        {"MOQ", "Minimum Order Quantity", "最小起订量：供应商接受的单笔订单最低数量，谈判核心条款之一。"},
        {"FOB", "Free On Board", "离岸价：卖方负责把货送到指定装运港并装船，之后运费与风险转给买方。"},
        {"CIF", "Cost, Insurance and Freight", "到岸价：卖方承担成本、保险费和到目的港的运费，风险仍在装运港转移。"},
        {"EXW", "Ex Works", "出厂价：买方自提，卖方在其仓库交货即完成义务，买方承担全程运输与出口手续。"},
        {"DDP", "Delivered Duty Paid", "完税交货：卖方负责运输、清关并缴纳关税后送货到门，买方收货即可。"},
        {"DDU", "Delivered Duty Unpaid", "未完税交货：卖方送到指定地点但不承担进口关税与清关税费。"},
        {"FCA", "Free Carrier", "货交承运人：卖方在指定地点把货交给买方指定的承运人即完成交货。"},
        {"L/C", "Letter of Credit", "信用证：银行担保付款的结算方式，单证相符即付款，大额贸易常用。"},
        {"T/T", "Telegraphic Transfer", "电汇：常见的汇付方式，常用 30% 定金 + 70% 见提单副本付款。"},
        {"D/P", "Documents against Payment", "付款交单：买方付款后银行才放提单等单据。"},
        {"D/A", "Documents against Acceptance", "承兑交单：买方承兑汇票后即可取单，风险高于 D/P。"},
        {"B/L", "Bill of Lading", "提单：承运人签发的货物收据与物权凭证，正本提单可控制货权。"},
        {"HS Code", "Harmonized System Code", "海关编码：国际通用商品分类编码，决定关税税率与监管条件。"},
        {"PO", "Purchase Order", "采购订单：买方正式下单凭证，写明品名、数量、价格、交期。"},
        {"PI", "Proforma Invoice", "形式发票：报价与合同之间的确认文件，可用于申请付款或进口许可。"},
        {"CI", "Commercial Invoice", "商业发票：清关与结算用的正式发票，金额需与合同一致。"},
        {"PL", "Packing List", "装箱单：列明每箱货物的品名、数量、毛净重与尺寸。"},
        {"CO", "Certificate of Origin", "原产地证：证明货物原产国，可享受协定关税优惠。"},
        {"ETA", "Estimated Time of Arrival", "预计到达时间：货物预计到达目的港/地的日期。"},
        {"ETD", "Estimated Time of Departure", "预计离港时间：货物预计从起运港开出的日期。"},
        {"OEM", "Original Equipment Manufacturer", "贴牌生产：按买方品牌要求生产产品。"},
        {"ODM", "Original Design Manufacturer", "原始设计制造：供应商提供现成设计并可贴牌，改动力度小。"},
        {"OBM", "Original Brand Manufacturer", "自有品牌制造：供应商以自有品牌销售产品。"},
        {"QC", "Quality Control", "质量控制：出货前验货流程，常见第三方验货（如 SGS 验厂验货）。"},
        {"SGS", "Société Générale de Surveillance", "通用公证行：国际第三方检验认证机构，出证常用于客户验货要求。"},
        {"CE", "Conformité Européenne", "欧盟合格标志：产品进入欧洲市场需符合相关指令并加贴 CE。"},
        {"FDA", "Food and Drug Administration", "美国食品药品监督管理局：食品、医疗器械等出口美国的主管机构。"},
        {"ROHS", "Restriction of Hazardous Substances", "有害物质限制指令：电子电气产品环保合规要求。"},
        {"RMA", "Return Material Authorization", "退货授权：售后退货前需取得的返修/退货编号。"},
        {"SOHO", "Small Office Home Office", "居家办公的小型外贸从业者，常指个人外贸业务员。"},
        {"OEM/ODM 打样", "Sampling", "按客户要求制作样品确认品质，样品费与运费归属常需提前谈定。"},
        {"交期", "Lead Time", "生产周期：从下单（或定金到位）到出货的时间，报价必须写明。"},
        {"背对背信用证", "Back-to-back L/C", "中间商凭收到的信用证向银行申请开出的第二张信用证给实际供货方。"},
        {"电放", "Telex Release", "电报放货：正本提单未签发或收回后凭电放指令提货，快捷但货权控制弱。"},
        {"甩柜", "Rolled Cargo", "原定船期舱位被取消或爆舱延至下航次，需关注 ETD 变动与改单费用。"},
        {"ASAP", "As Soon As Possible", "尽快：聊天与邮件中常用的催办用语。"},
        {"FW", "Forward", "转发：邮件主题前缀，表示转发的往来邮件。"},
        {"RE", "Reply", "回复：邮件主题前缀，表示对前一封邮件的答复。"},
        {"CC", "Carbon Copy", "抄送：邮件抄送第三人，知会进度时常用。"},
        {"E&OE", "Errors and Omissions Excepted", "允许误差/错误除外：报价单上声明如有笔误可更正。"},
        {"Ref", "Reference", "参考编号：用于单据、询盘、订单的唯一编号追踪。"},
        {"Tare / Net / Gross", "皮重 / 净重 / 毛重", "重量三要素：包装重量、货物净重、加包装后总重，清关与运费依据。"},
    };
}
