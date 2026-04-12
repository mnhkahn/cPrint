# cPrint Android USB打印驱动技术调研报告

## 目录
1. [概述](#概述)
2. [Android USB Host API](#1-android-usb-host-api)
3. [USB打印机通信协议](#2-usb打印机通信协议)
4. [主流打印机VID/PID列表](#3-主流打印机vidpid列表)
5. [Android打印框架集成](#4-android打印框架集成)
6. [开源方案调研](#5-开源方案调研)
7. [权限和安全性](#6-权限和安全性)
8. [推荐架构方案](#7-推荐架构方案)
9. [关键代码示例](#8-关键代码示例)
10. [兼容性问题和解决方案](#9-兼容性问题和解决方案)
11. [依赖库列表](#10-依赖库列表)

---

## 概述

cPrint项目需要实现Android设备通过USB Type-C接口连接打印机进行打印。本报告调研了Android USB打印驱动的技术方案，包括原生API使用、通信协议、开源库和架构设计。

### 核心挑战
- Android设备作为USB Host与打印机通信
- 兼容多种打印机品牌和PDL语言
- 处理USB权限和动态授权
- 集成Android原生打印框架

---

## 1. Android USB Host API

### 1.1 API概述

Android从3.1（API Level 12）开始支持USB Host模式，核心类包括：

| 类名 | 功能 |
|------|------|
| `UsbManager` | USB设备管理，权限控制 |
| `UsbDevice` | 表示连接的USB设备 |
| `UsbInterface` | USB设备接口描述 |
| `UsbEndpoint` | USB端点（输入/输出） |
| `UsbDeviceConnection` | 实际USB连接通道 |
| `UsbRequest` | 异步USB请求 |

### 1.2 打印机USB接口特征

USB打印机通常使用以下配置：
- **Interface Class**: 7 (Printer Class)
- **Interface SubClass**: 1 (Printer)
- **Interface Protocol**: 1 (Unidirectional) 或 2 (Bidirectional)
- **端点配置**:
  - Bulk OUT端点：发送打印数据
  - Bulk IN端点：接收打印机状态（双向模式）

### 1.3 设备发现和连接流程

```java
// 获取UsbManager
UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

// 查找打印机设备
HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
for (UsbDevice device : deviceList.values()) {
    if (isPrinter(device)) {
        // 请求权限
        PendingIntent permissionIntent = PendingIntent.getBroadcast(
            context, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
        usbManager.requestPermission(device, permissionIntent);
    }
}

// 判断是否为打印机
private boolean isPrinter(UsbDevice device) {
    for (int i = 0; i < device.getInterfaceCount(); i++) {
        UsbInterface intf = device.getInterface(i);
        if (intf.getInterfaceClass() == UsbConstants.USB_CLASS_PRINTER) {
            return true;
        }
    }
    return false;
}
```

---

## 2. USB打印机通信协议

### 2.1 PDL (Page Description Language) 页面描述语言

#### 2.1.1 PCL (Printer Command Language) - HP

**版本演进**:
- PCL 3: 基础文本和图形
- PCL 5: 新增矢量图形、TrueType字体支持
- PCL 6 (PCL XL): 二进制格式，更高效

**基本PCL命令**:
```
ESC E          - 复位打印机
ESC &l0O       - 纵向打印
ESC &l1O       - 横向打印
ESC &l#P       - 设置页数 (#=0连续)
ESC *p#X       - 水平定位 (#=点数)
ESC *p#Y       - 垂直定位 (#=点数)
ESC *c#D       - 设置字体 (#=字体ID)
ESC (s#H       - 设置字号 (#=1/1440英寸)
ESC &a#C       - 绝对水平位置
ESC &a#R       - 绝对垂直位置
```

**PCL XL示例结构**:
```
// PCL XL二进制流头
@PJL ENTER LANGUAGE = PCL XL
// 操作符序列
BeginSession
    PageHeader
        SetColorSpace
        BeginImage
            ReadImage
        EndImage
    EndPage
EndSession
```

#### 2.1.2 PostScript - Adobe

**特点**:
- 基于堆栈的编程语言
- 矢量图形和文本描述
- 需要打印机内置解释器

**基本PostScript结构**:
```postscript
%!PS-Adobe-3.0
%%Title: Sample Document
%%BoundingBox: 0 0 612 792

% 设置字体
/Helvetica findfont 12 scalefont setfont

% 移动并显示文本
72 700 moveto
(Hello World) show

% 绘制矩形
0 0 1 setrgbcolor
100 100 200 200 rectfill

showpage
%%EOF
```

#### 2.1.3 ESC/P (Epson Standard Code for Printers)

**基本命令**:
```
ESC @          - 初始化打印机
ESC M          - 选择字体 (0=草稿, 1=Roman, 2=Sans Serif)
ESC ! n        - 综合设置 (n=位图组合)
ESC - n        - 下划线 (0=关, 1=1点, 2=2点)
ESC E          - 粗体开
ESC F          - 粗体关
ESC 4          - 斜体开
ESC 5          - 斜体关
ESC S 0        - 上标
ESC S 1        - 下标
ESC T          - 取消上下标
ESC $ nL nH    - 绝对水平定位
ESC D n1...nk NUL - 设置制表位
ESC J n        - 进纸n/180英寸
```

**ESC/P-R (Epson ESC/P-Raster)**:
- 专为喷墨打印机设计
- 支持光栅图像数据
- 命令格式：`ESC ( r #1 #2 #3 #4 <data>`

#### 2.1.4 打印机语言选择策略

```java
public enum PDLType {
    PCL5,       // HP LaserJet兼容
    PCL6_XL,    // HP现代打印机
    POSTSCRIPT, // Adobe兼容
    ESCP,       // Epson针式/喷墨
    ESCPR,      // Epson ESC/P-R
    SPL,        // Samsung Printer Language
    UFR,        // Canon UFR
    GDI         // 主机渲染
}

public PDLType detectPDL(UsbDevice device) {
    int vendorId = device.getVendorId();
    int productId = device.getProductId();
    
    // HP打印机
    if (vendorId == 0x03F0) {
        if (isLaserJet(productId)) return PDLType.PCL6_XL;
        return PDLType.PCL5;
    }
    
    // Epson打印机
    if (vendorId == 0x04B8) {
        if (isInkJet(productId)) return PDLType.ESCPR;
        return PDLType.ESCP;
    }
    
    // Canon打印机
    if (vendorId == 0x04A9) {
        return PDLType.UFR;  // 或PCL5
    }
    
    // Brother打印机
    if (vendorId == 0x04F9) {
        return PDLType.ESCP;  // Brother使用ESC/P变体
    }
    
    return PDLType.PCL5;  // 默认
}
```

### 2.2 IPP-over-USB协议

**协议概述**:
- 基于USB的IPP (Internet Printing Protocol)
- 使用USB CDC (Communication Device Class)
- 端点配置：Bulk IN/OUT + Interrupt IN

**协议栈**:
```
应用层: IPP请求/响应 (HTTP-like)
传输层: USB Bulk传输
设备层: USB CDC或Vendor Specific
```

**IPP操作码**:
```
0x0001 - Print-Job
0x0002 - Print-URI
0x0003 - Validate-Job
0x0004 - Create-Job
0x0005 - Send-Document
0x0006 - Send-URI
0x0007 - Cancel-Job
0x0008 - Get-Job-Attributes
0x0009 - Get-Jobs
0x000B - Get-Printer-Attributes
```

**Android实现要点**:
```java
// IPP-over-USB需要实现HTTP-over-USB
// 使用USB Bulk端点传输IPP数据

public class IppOverUsbTransport {
    private UsbDeviceConnection connection;
    private UsbEndpoint bulkOut;
    private UsbEndpoint bulkIn;
    
    public byte[] sendIppRequest(byte[] ippData) {
        // 构建HTTP wrapper
        byte[] httpRequest = buildHttpPost(ippData);
        
        // 通过USB发送
        connection.bulkTransfer(bulkOut, httpRequest, httpRequest.length, 5000);
        
        // 接收响应
        byte[] response = new byte[4096];
        int received = connection.bulkTransfer(bulkIn, response, response.length, 5000);
        
        return parseIppResponse(response, received);
    }
}
```

### 2.3 原生USB打印协议 (USB Printer Class)

**IEEE 1284标准**:
- 定义了USB打印设备的行为
- 支持双向通信获取状态

**设备请求**:
```
GET_DEVICE_ID (0x00): 获取IEEE 1284设备ID字符串
GET_PORT_STATUS (0x01): 获取打印机状态
SOFT_RESET (0x02): 软复位
```

**获取设备ID示例**:
```java
// 使用控制传输获取IEEE 1284设备ID
byte[] buffer = new byte[256];
int length = connection.controlTransfer(
    UsbConstants.USB_DIR_IN | UsbConstants.USB_TYPE_CLASS,
    0x00,  // GET_DEVICE_ID
    0,     // Interface
    usbInterface.getId(),
    buffer,
    buffer.length,
    5000
);

// 解析设备ID字符串
String deviceId = new String(buffer, 2, length - 2); // 跳过前两个字节长度
// 格式: MFG:厂商;MDL:型号;CMD:支持命令;...
```

---

## 3. 主流打印机VID/PID列表

### 3.1 Vendor ID列表

| 厂商 | VID (十六进制) | VID (十进制) |
|------|----------------|--------------|
| HP (Hewlett-Packard) | 0x03F0 | 1008 |
| Epson | 0x04B8 | 1208 |
| Canon | 0x04A9 | 1193 |
| Brother | 0x04F9 | 1273 |
| Samsung | 0x04E8 | 1256 |
| Xerox | 0x0924 | 2340 |
| Lexmark | 0x043D | 1085 |
| Dell | 0x413C | 16700 |
| Ricoh | 0x05CA | 1482 |
| Kyocera | 0x0482 | 1154 |
| Konica Minolta | 0x132B | 4907 |
| OKI | 0x06BC | 1724 |
| Panasonic | 0x04DA | 1242 |
| Sharp | 0x04DD | 1245 |
| Toshiba | 0x1179 | 4729 |

### 3.2 HP打印机常见PID

```java
// HP LaserJet系列
0x0017, // LaserJet 1000
0x0024, // LaserJet 1005
0x1312, // LaserJet P1005
0x1313, // LaserJet P1006
0x1314, // LaserJet P1007
0x1315, // LaserJet P1008
0x1316, // LaserJet P1505
0x1317, // LaserJet P1505n
0x131A, // LaserJet P1002
0x131B, // LaserJet P1003
0x131C, // LaserJet P1004
0x131D, // LaserJet P1009
0x17A7, // LaserJet Pro MFP M125nw
0x17A9, // LaserJet Pro MFP M127fn
0x2B7A, // LaserJet Pro M12a
0x2B7B, // LaserJet Pro M12w
0x0F94, // LaserJet 1020
0x0F95, // LaserJet 1022
0x0F96, // LaserJet 1018
0x0F97, // LaserJet 1022n
0x0F98, // LaserJet 1022nw
0x0F9A, // LaserJet 1015
0x0F9B, // LaserJet 1012
0x0F9C, // LaserJet 1010
0x0F9D, // LaserJet 1018
0x0F9E, // LaserJet 1020 Plus

// HP DeskJet/InkJet系列
0x002A, // DeskJet 3320
0x002B, // DeskJet 3420
0x002C, // DeskJet 3550
0x002D, // DeskJet 3650
0x002E, // DeskJet 3740
0x002F, // DeskJet 3840
0x0030, // DeskJet 3940
0x0031, // DeskJet 1220C
0x0032, // DeskJet 930C
0x0033, // DeskJet 950C
0x0034, // DeskJet 970Cxi
0x0035, // DeskJet 990Cxi
0x0036, // DeskJet 1180C
0x0037, // DeskJet 6122
0x0038, // DeskJet 6127
0x0039, // DeskJet 5550
0x003A, // DeskJet 5650
0x003B, // DeskJet 5150
0x003C, // DeskJet 5850
0x003D, // DeskJet 9650
0x003E, // DeskJet 9670
0x003F, // DeskJet 9680
```

### 3.3 Epson打印机常见PID

```java
// Epson Stylus系列
0x0001, // Stylus Color
0x0002, // Stylus Color 400
0x0005, // Stylus Color 600
0x0007, // Stylus Color 800
0x0008, // Stylus Color 850
0x000A, // Stylus Color 900
0x000B, // Stylus Color 980
0x000C, // Stylus Color 3000
0x0011, // Stylus Pro 5000
0x0019, // Stylus Color 440
0x001A, // Stylus Color 640
0x001B, // Stylus Color 660
0x001C, // Stylus Color 670
0x001D, // Stylus Photo 750
0x001E, // Stylus Photo 1200
0x001F, // Stylus Photo 890
0x0020, // Stylus Photo 895
0x0021, // Stylus Photo 915
0x0022, // Stylus Photo 925
0x0023, // Stylus Photo 935

// Epson WorkForce系列
0x0005, // WorkForce 30
0x0006, // WorkForce 40
0x0007, // WorkForce 600
0x0008, // WorkForce 500
0x0009, // WorkForce 310
0x000A, // WorkForce 315
0x000B, // WorkForce 320
0x000C, // WorkForce 325
0x000D, // WorkForce 545
0x000E, // WorkForce 645
0x000F, // WorkForce 840
0x0010, // WorkForce 845
0x0011, // WorkForce 3520
0x0012, // WorkForce 3540
0x0013, // WorkForce 7010
0x0014, // WorkForce 7510
0x0015, // WorkForce 7520
0x0016, // WorkForce 3530
0x0017, // WorkForce 3550
0x0018, // WorkForce 3620
0x0019, // WorkForce 3640
0x001A, // WorkForce 7110
0x001B, // WorkForce 7610
0x001C, // WorkForce 7620
0x001D, // WorkForce 7710
0x001E, // WorkForce 7720
0x001F, // WorkForce 7210
0x0020, // WorkForce 7715
0x0021, // WorkForce 3720
0x0022, // WorkForce 3730
0x0023, // WorkForce 3740
0x0024, // WorkForce 4630
0x0025, // WorkForce 4640
0x0026, // WorkForce 4730
0x0027, // WorkForce 4740
0x0028, // WorkForce 5110
0x0029, // WorkForce 5190
0x002A, // WorkForce 5690
0x002B, // WorkForce 5210
0x002C, // WorkForce 5790
0x002D, // WorkForce 2860
0x002E, // WorkForce 4820
0x002F, // WorkForce 4830
0x0030, // WorkForce 4840
0x0031, // WorkForce 7310
0x0032, // WorkForce 7830
0x0033, // WorkForce 7840
0x0034, // WorkForce 7850
0x0035, // WorkForce 7860

// Epson L系列 (墨仓式)
0x0065, // L100
0x0066, // L200
0x0067, // L300
0x0068, // L400
0x0069, // L110
0x006A, // L210
0x006B, // L350
0x006C, // L355
0x006D, // L550
0x006E, // L555
0x006F, // L800
0x0070, // L801
0x0071, // L1800
0x0072, // L130
0x0073, // L220
0x0074, // L310
0x0075, // L360
0x0076, // L365
0x0077, // L455
0x0078, // L565
0x0079, // L655
0x007A, // L805
0x007B, // L850
0x007C, // L1110
0x007D, // L3110
0x007E, // L3150
0x007F, // L3160
0x0080, // L4150
0x0081, // L4160
0x0082, // L6160
0x0083, // L6170
0x0084, // L6190
0x0085, // L14150
0x0086, // L15150
0x0087, // L15160
0x0088, // L1210
0x0089, // L3210
0x008A, // L3250
0x008B, // L3260
0x008C, // L4260
0x008D, // L6260
0x008E, // L6270
0x008F, // L6290
0x0090, // L6490
0x0091, // L15158
0x0092, // L15168
```

### 3.4 Canon打印机常见PID

```java
// Canon PIXMA系列
0x1048, // PIXMA iP1000
0x1049, // PIXMA iP1500
0x104A, // PIXMA iP2000
0x104B, // PIXMA iP3000
0x104C, // PIXMA iP4000
0x104D, // PIXMA iP5000
0x104E, // PIXMA iP6000D
0x104F, // PIXMA iP8500
0x1050, // PIXMA iP2200
0x1051, // PIXMA iP4200
0x1052, // PIXMA iP5200
0x1053, // PIXMA iP6600D
0x1054, // PIXMA iP7500
0x1055, // PIXMA iP1600
0x1056, // PIXMA iP2600
0x1057, // PIXMA iP6210D
0x1058, // PIXMA iP6220D
0x1059, // PIXMA iP5200R
0x105A, // PIXMA iP3300
0x105B, // PIXMA iP4300
0x105C, // PIXMA iP5300
0x105D, // PIXMA iP90
0x105E, // PIXMA iP1800
0x105F, // PIXMA iP2500
0x1060, // PIXMA iP3500
0x1061, // PIXMA iP4500
0x1062, // PIXMA iP100
0x1063, // PIXMA iP1900
0x1064, // PIXMA iP3600
0x1065, // PIXMA iP4600
0x1066, // PIXMA iP4700
0x1067, // PIXMA iP2700
0x1068, // PIXMA iP4850
0x1069, // PIXMA iP4950
0x106A, // PIXMA iP2702
0x106B, // PIXMA iP100v
0x106C, // PIXMA iP110
0x106D, // PIXMA iP2820
0x106E, // PIXMA iP2850
0x106F, // PIXMA iP2870
0x1070, // PIXMA iP2872
0x1071, // PIXMA iP110v

// Canon imageCLASS系列
0x261A, // imageCLASS LBP6000
0x261B, // imageCLASS LBP6020
0x261C, // imageCLASS LBP6030
0x261D, // imageCLASS LBP6040
0x261E, // imageCLASS LBP6230
0x261F, // imageCLASS LBP151dw
0x2620, // imageCLASS LBP6030w
0x2621, // imageCLASS LBP6040w
0x2622, // imageCLASS LBP6230dw
0x2623, // imageCLASS LBP151
0x2624, // imageCLASS LBP214dw
0x2625, // imageCLASS LBP215dw
0x2626, // imageCLASS LBP236dw
0x2627, // imageCLASS LBP237dw
0x2628, // imageCLASS LBP246dw
0x2629, // imageCLASS LBP247dw
0x262A, // imageCLASS LBP122dw
0x262B, // imageCLASS LBP124dw
0x262C, // imageCLASS LBP125dw
```

### 3.5 Brother打印机常见PID

```java
// Brother HL系列 (激光)
0x0017, // HL-1440
0x0018, // HL-1450
0x0019, // HL-1470N
0x001A, // HL-1650
0x001B, // HL-1670N
0x001C, // HL-1850
0x001D, // HL-1870N
0x001E, // HL-2460
0x001F, // HL-2600CN
0x0020, // HL-3450CN
0x0021, // HL-2700CN
0x0022, // HL-3040CN
0x0023, // HL-3070CW
0x0024, // HL-4040CN
0x0025, // HL-4070CDW
0x0026, // HL-5340D
0x0027, // HL-5350DN
0x0028, // HL-5370DW
0x0029, // HL-5380DN
0x002A, // HL-2140
0x002B, // HL-2150N
0x002C, // HL-2170W
0x002D, // HL-2240
0x002E, // HL-2240D
0x002F, // HL-2250DN
0x0030, // HL-2270DW
0x0031, // HL-2130
0x0032, // HL-2132
0x0033, // HL-2135W
0x0034, // HL-1110
0x0035, // HL-1112
0x0036, // HL-1210W
0x0037, // HL-1212W
0x0038, // HL-L2300D
0x0039, // HL-L2340DW
0x003A, // HL-L2360DN
0x003B, // HL-L2365DW
0x003C, // HL-L2380DW
0x003D, // HL-L5100DN
0x003E, // HL-L5200DW
0x003F, // HL-L6200DW
0x0040, // HL-L6400DW
0x0041, // HL-L3210CW
0x0042, // HL-L3230CDW
0x0043, // HL-L3270CDW
0x0044, // HL-L3290CDW
0x0045, // HL-L8260CDW
0x0046, // HL-L8360CDW
0x0047, // HL-L9310CDW
0x0048, // HL-L2350DW
0x0049, // HL-L2370DW
0x004A, // HL-L2370DW XL
0x004B, // HL-L2390DW
0x004C, // HL-L2395DW

// Brother DCP系列 (多功能)
0x0050, // DCP-7020
0x0051, // DCP-7025
0x0052, // DCP-8060
0x0053, // DCP-8065DN
0x0054, // DCP-9040CN
0x0055, // DCP-9045CDN
0x0056, // DCP-7055
0x0057, // DCP-7055W
0x0058, // DCP-7060D
0x0059, // DCP-7065DN
0x005A, // DCP-7070DW
0x005B, // DCP-8080DN
0x005C, // DCP-8085DN
0x005D, // DCP-9010CN
0x005E, // DCP-9042CDN
0x005F, // DCP-J315W
0x0060, // DCP-J515W
0x0061, // DCP-J715W
0x0062, // DCP-J125
0x0063, // DCP-J140W
0x0064, // DCP-J525W
0x0065, // DCP-J725DW
0x0066, // DCP-J925DW
0x0067, // DCP-1510
0x0068, // DCP-1512
0x0069, // DCP-1610W
0x006A, // DCP-1612W
0x006B, // DCP-L2520DW
0x006C, // DCP-L2540DW
0x006D, // DCP-L2560DW
0x006E, // DCP-9020CDN
0x006F, // DCP-J4120DW
0x0070, // DCP-J562DW
0x0071, // DCP-L3510CDW
0x0072, // DCP-L3550CDW
0x0073, // DCP-L8410CDW
0x0074, // DCP-L3517CDW
0x0075, // DCP-J772DW
0x0076, // DCP-J774DW
0x0077, // DCP-L2530DW
0x0078, // DCP-L2550DN
0x0079, // DCP-L2510D
0x007A, // DCP-L2537DW
```

### 3.6 Samsung打印机常见PID

```java
// Samsung ML系列
0x325B, // ML-1610
0x325C, // ML-2010
0x325D, // ML-2510
0x325E, // ML-2570
0x325F, // ML-3050
0x3260, // ML-3470
0x3261, // ML-3471
0x3262, // ML-1630
0x3263, // ML-2850
0x3264, // ML-2851
0x3265, // ML-1910
0x3266, // ML-1915
0x3267, // ML-2525
0x3268, // ML-2525W
0x3269, // ML-2580N
0x326A, // ML-2950
0x326B, // ML-2955
0x326C, // ML-2160
0x326D, // ML-2165
0x326E, // ML-2165W
0x326F, // ML-2168
0x3270, // ML-2168W
0x3271, // ML-3310
0x3272, // ML-3310ND
0x3273, // ML-3710
0x3274, // ML-3710ND
0x3275, // ML-3750
0x3276, // ML-3750ND

// Samsung SCX系列 (多功能)
0x3277, // SCX-3200
0x3278, // SCX-3205
0x3279, // SCX-3205W
0x327A, // SCX-4623F
0x327B, // SCX-4623FN
0x327C, // SCX-4623FW
0x327D, // SCX-4824FN
0x327E, // SCX-4828FN
0x327F, // SCX-4650
0x3280, // SCX-4655
0x3281, // SCX-4726
0x3282, // SCX-4728
0x3283, // SCX-4729
0x3284, // SCX-3405
0x3285, // SCX-3405W
0x3286, // SCX-3405F
0x3287, // SCX-3405FW
0x3288, // SCX-4726FD
0x3289, // SCX-4726FN
0x328A, // SCX-4728FD
0x328B, // SCX-4728FN
0x328C, // SCX-4729FD
0x328D, // SCX-4729FW
0x328E, // SCX-5637FR
0x328F, // SCX-5639FR
0x3290, // SCX-5737FW
0x3291, // SCX-5739FW
0x3292, // SCX-5835FN
0x3293, // SCX-5935FN
0x3294, // SCX-6555N
0x3295, // SCX-6555NX
0x3296, // SCX-6345N
0x3297, // SCX-6345NX
0x3298, // SCX-8240
0x3299, // SCX-8240NA
```

### 3.7 Xerox打印机常见PID

```java
// Xerox Phaser系列
0x4215, // Phaser 3117
0x4216, // Phaser 3122
0x4217, // Phaser 3124
0x4218, // Phaser 3125
0x4219, // Phaser 3200MFP
0x421A, // Phaser 3250
0x421B, // Phaser 3300MFP
0x421C, // Phaser 3435
0x421D, // Phaser 3500
0x421E, // Phaser 3600
0x421F, // Phaser 6110
0x4220, // Phaser 6120
0x4221, // Phaser 6125
0x4222, // Phaser 6130
0x4223, // Phaser 6140
0x4224, // Phaser 6500
0x4225, // Phaser 7500
0x4226, // Phaser 7800
0x4227, // Phaser 7100
0x4228, // Phaser 6700
0x4229, // Phaser 6600
0x422A, // Phaser 4620
0x422B, // Phaser 4600
0x422C, // Phaser 3610
0x422D, // Phaser 4600N
0x422E, // Phaser 4620N
0x422F, // Phaser 4622

// Xerox WorkCentre系列
0x4230, // WorkCentre 3210
0x4231, // WorkCentre 3220
0x4232, // WorkCentre 3315
0x4233, // WorkCentre 3325
0x4234, // WorkCentre 3655
0x4235, // WorkCentre 3655i
0x4236, // WorkCentre 3615
0x4237, // WorkCentre 3615DN
0x4238, // WorkCentre 6025
0x4239, // WorkCentre 6027
0x423A, // WorkCentre 6025BI
0x423B, // WorkCentre 6027BI
0x423C, // WorkCentre 6515
0x423D, // WorkCentre 6515DN
0x423E, // WorkCentre 6515DNI
0x423F, // WorkCentre 6515N
0x4240, // WorkCentre 6515NI
0x4241, // WorkCentre 6655
0x4242, // WorkCentre 6655i
0x4243, // WorkCentre 6655DN
0x4244, // WorkCentre 6655DNI
```

---

## 4. Android打印框架集成

### 4.1 PrintManager和PrintService概述

Android 4.4 (API 19) 引入了完整的打印框架：

```
应用层: PrintManager (打印任务管理)
        |
服务层: PrintService (自定义打印服务)
        |
驱动层: USB设备通信
```

### 4.2 创建自定义PrintService

```java
// 1. 继承PrintService
public class UsbPrintService extends PrintService {
    
    private UsbManager usbManager;
    private Map<String, UsbPrinter> connectedPrinters = new HashMap<>();
    
    @Override
    protected void onConnected() {
        super.onConnected();
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        registerUsbReceiver();
        discoverPrinters();
    }
    
    @Override
    protected void onDisconnected() {
        super.onDisconnected();
        unregisterUsbReceiver();
    }
    
    // 发现打印机时添加
    @Override
    protected PrinterDiscoverySession onCreatePrinterDiscoverySession() {
        return new UsbPrinterDiscoverySession();
    }
    
    // 处理打印请求
    @Override
    protected void onPrintJobQueued(PrintJob printJob) {
        new Thread(() -> processPrintJob(printJob)).start();
    }
    
    @Override
    protected void onRequestCancelPrintJob(PrintJob printJob) {
        printJob.cancel();
    }
}

// 2. 打印机发现会话
public class UsbPrinterDiscoverySession extends PrinterDiscoverySession {
    
    @Override
    public void onStartPrinterDiscovery(List<PrinterId> priorityList) {
        // 扫描USB设备
        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> devices = usbManager.getDeviceList();
        
        List<PrinterInfo> printers = new ArrayList<>();
        for (UsbDevice device : devices.values()) {
            if (isPrinter(device)) {
                PrinterId printerId = generatePrinterId(device);
                PrinterInfo printer = new PrinterInfo.Builder(printerId, 
                    getPrinterName(device), PrinterInfo.STATUS_IDLE)
                    .setCapabilities(buildCapabilities(device))
                    .build();
                printers.add(printer);
            }
        }
        
        addPrinters(printers);
    }
    
    @Override
    public void onStopPrinterDiscovery() {
        // 停止扫描
    }
    
    @Override
    public void onValidatePrinters(List<PrinterId> printerIds) {
        // 验证打印机状态
    }
    
    @Override
    public void onStartPrinterStateTracking(PrinterId printerId) {
        // 开始监控打印机状态
    }
    
    @Override
    public void onStopPrinterStateTracking(PrinterId printerId) {
        // 停止监控
    }
    
    @Override
    public void onDestroy() {
        // 清理资源
    }
}
```

### 4.3 AndroidManifest配置

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.cprint.usb">
    
    <!-- USB权限 -->
    <uses-feature android:name="android.hardware.usb.host" android:required="true"/>
    <uses-permission android:name="android.permission.USB_PERMISSION"/>
    
    <!-- 打印服务权限 -->
    <uses-permission android:name="android.permission.BIND_PRINT_SERVICE"/>
    
    <application
        android:name=".CPrintApplication"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name">
        
        <!-- USB设备连接广播接收器 -->
        <receiver android:name=".UsbDeviceReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.hardware.usb.action.USB_DEVICE_ATTACHED"/>
            </intent-filter>
            <meta-data android:name="android.hardware.usb.action.USB_DEVICE_ATTACHED"
                android:resource="@xml/usb_device_filter"/>
        </receiver>
        
        <!-- 打印服务声明 -->
        <service android:name=".UsbPrintService"
            android:permission="android.permission.BIND_PRINT_SERVICE"
            android:exported="true">
            <intent-filter>
                <action android:name="android.printservice.PrintService"/>
            </intent-filter>
            <meta-data android:name="android.printservice"
                android:resource="@xml/print_service"/>
        </service>
        
    </application>
</manifest>
```

### 4.4 打印服务配置 (res/xml/print_service.xml)

```xml
<?xml version="1.0" encoding="utf-8"?>
<print-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:vendor="cPrint"
    android:settingsActivity="com.cprint.usb.SettingsActivity"
    android:addPrintersActivity="com.cprint.usb.AddPrintersActivity"
    android:advancedPrintOptionsActivity="com.cprint.usb.AdvancedOptionsActivity"
    android:enableVendorMetrics="true"/>
```

### 4.5 USB设备过滤器 (res/xml/usb_device_filter.xml)

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- HP打印机 -->
    <usb-device vendor-id="1008" product-id="0"/>
    <usb-device vendor-id="1008" product-id="4122"/>
    <usb-device vendor-id="1008" product-id="4884"/>
    
    <!-- Epson打印机 -->
    <usb-device vendor-id="1208" product-id="0"/>
    <usb-device vendor-id="1208" product-id="1"/>
    <usb-device vendor-id="1208" product-id="101"/>
    
    <!-- Canon打印机 -->
    <usb-device vendor-id="1193" product-id="0"/>
    <usb-device vendor-id="1193" product-id="4168"/>
    <usb-device vendor-id="1193" product-id="9760"/>
    
    <!-- Brother打印机 -->
    <usb-device vendor-id="1273" product-id="0"/>
    <usb-device vendor-id="1273" product-id="23"/>
    <usb-device vendor-id="1273" product-id="56"/>
    
    <!-- Samsung打印机 -->
    <usb-device vendor-id="1256" product-id="0"/>
    <usb-device vendor-id="1256" product-id="12891"/>
    
    <!-- Xerox打印机 -->
    <usb-device vendor-id="2340" product-id="0"/>
    <usb-device vendor-id="2340" product-id="16917"/>
    
    <!-- 通用打印机类 -->
    <usb-device class="7" subclass="1" protocol="0"/>
    <usb-device class="7" subclass="1" protocol="1"/>
    <usb-device class="7" subclass="1" protocol="2"/>
    <usb-device class="7" subclass="1" protocol="3"/>
</resources>
```

---

## 5. 开源方案调研

### 5.1 CUPS/libusb移植可行性

#### 5.1.1 CUPS (Common Unix Printing System)

**架构分析**:
```
CUPS架构:
- cupsd: 打印调度守护进程
- cups-browsed: 打印机发现
- libcups: 客户端库
- filters: 文档转换过滤器
- backends: 设备通信后端
```

**Android移植挑战**:

| 组件 | 移植难度 | 说明 |
|------|----------|------|
| libcups | 低 | 纯C代码，可NDK编译 |
| cupsd | 中 | 需要Unix域套接字支持 |
| filters | 高 | 依赖Ghostscript等 |
| backends | 中 | USB后端需适配Android USB API |

**推荐移植方案**:
```
方案A: 完整CUPS移植 (复杂，功能完整)
- 使用Termux或chroot环境
- 需要root权限
- 适合专业用户

方案B: 精简CUPS客户端 (推荐)
- 只移植libcups
- 实现自定义USB后端
- 无需root权限

方案C: 纯Java实现 (最简单)
- 不依赖CUPS
- 直接调用Android USB API
- 维护成本低
```

#### 5.1.2 libusb移植

**状态**: libusb 1.0.24+ 官方支持Android

**集成步骤**:
```cmake
# CMakeLists.txt
find_package(libusb REQUIRED)

add_library(usb-backend SHARED
    usb_backend.c
)

target_link_libraries(usb-backend
    libusb::libusb
    android
    log
)
```

**权限要求**:
- Android 4.0-4.4: 需要root权限
- Android 5.0+: 可通过UsbManager获取文件描述符，配合libusb使用

**使用方式**:
```c
// 通过Android UsbManager获取fd，传递给libusb
int fd = ...; // 从UsbDeviceConnection获取
libusb_device_handle *handle;
libusb_wrap_sys_device(NULL, (intptr_t)fd, &handle);
```

### 5.2 ippusbxd工具

**功能**: 将IPP-over-USB设备暴露为网络IPP打印机

**Android适用性**:
- 需要root权限运行守护进程
- 适合开发调试，不适合生产环境
- 可考虑移植核心功能到Java层

### 5.3 现有Android打印库

#### 5.3.1 Google Cloud Print替代方案

| 方案 | 类型 | 特点 |
|------|------|------|
| CUPS Cloud Print | 桥接 | 将CUPS打印机暴露到云端 |
| Mobility Print | 商业 | PaperCut出品，免费版可用 |
| PrinterShare | 商业 | 成熟的Android打印方案 |
| Mopria Print Service | 标准 | 行业联盟标准 |

#### 5.3.2 开源Android打印项目

**1. cups4j**
- Java CUPS客户端库
- 支持IPP协议
- 不依赖本地CUPS

**2. jipp**
- 纯Java IPP实现
- 支持IPP 2.0
- 适合IPP-over-USB

**3. Android-USB-Printer**
- 直接USB通信示例
- 支持ESC/POS
- 适合热敏打印机

### 5.4 推荐技术栈

```
cPrint USB打印方案:

┌─────────────────────────────────────────────────────┐
│  应用层: PrintManager + 自定义PrintService          │
├─────────────────────────────────────────────────────┤
│  协议层: IPP客户端 (jipp) + PDL生成器               │
├─────────────────────────────────────────────────────┤
│  传输层: Android USB Host API                       │
│          (UsbManager/UsbDeviceConnection)           │
├─────────────────────────────────────────────────────┤
│  设备层: USB打印机 (Bulk Transfer)                  │
└─────────────────────────────────────────────────────┘
```

---

## 6. 权限和安全性

### 6.1 AndroidManifest权限配置

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    
    <!-- 基础USB权限 -->
    <uses-feature android:name="android.hardware.usb.host" android:required="true"/>
    
    <!-- 打印服务权限 -->
    <uses-permission android:name="android.permission.BIND_PRINT_SERVICE"/>
    
    <!-- 网络权限 (IPP-over-USB不需要，但IPP-over-Network需要) -->
    <uses-permission android:name="android.permission.INTERNET"/>
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>
    
    <!-- 存储权限 (读取打印文档) -->
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"/>
    <uses-permission android:name="android.permission.READ_MEDIA_IMAGES"/>
    <uses-permission android:name="android.permission.READ_MEDIA_VIDEO"/>
    <uses-permission android:name="android.permission.READ_MEDIA_DOCUMENTS"/>
    
    <!-- 通知权限 (Android 13+) -->
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
    
</manifest>
```

### 6.2 USB权限动态申请

```java
public class UsbPermissionManager {
    
    private static final String ACTION_USB_PERMISSION = 
        "com.cprint.usb.USB_PERMISSION";
    
    private Context context;
    private UsbManager usbManager;
    private BroadcastReceiver permissionReceiver;
    
    public interface PermissionCallback {
        void onPermissionGranted(UsbDevice device);
        void onPermissionDenied(UsbDevice device);
    }
    
    public void requestPermission(UsbDevice device, PermissionCallback callback) {
        if (usbManager.hasPermission(device)) {
            callback.onPermissionGranted(device);
            return;
        }
        
        // 注册广播接收器
        permissionReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (ACTION_USB_PERMISSION.equals(action)) {
                    synchronized (this) {
                        UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            callback.onPermissionGranted(device);
                        } else {
                            callback.onPermissionDenied(device);
                        }
                        context.unregisterReceiver(this);
                    }
                }
            }
        };
        
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        context.registerReceiver(permissionReceiver, filter);
        
        // 请求权限
        PendingIntent permissionIntent = PendingIntent.getBroadcast(
            context, 0, new Intent(ACTION_USB_PERMISSION), 
            PendingIntent.FLAG_IMMUTABLE);
        usbManager.requestPermission(device, permissionIntent);
    }
}
```

### 6.3 USB设备连接处理

```java
public class UsbDeviceReceiver extends BroadcastReceiver {
    
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        
        if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
            UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if (isPrinter(device)) {
                // 通知用户发现打印机
                showPrinterConnectedNotification(context, device);
                // 请求权限
                requestUsbPermission(context, device);
            }
        } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
            UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            // 清理连接资源
            cleanupPrinterConnection(device);
        }
    }
    
    private boolean isPrinter(UsbDevice device) {
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface intf = device.getInterface(i);
            if (intf.getInterfaceClass() == UsbConstants.USB_CLASS_PRINTER) {
                return true;
            }
        }
        return false;
    }
}
```

### 6.4 安全性考虑

**数据传输安全**:
```java
// USB通信不需要加密（物理连接）
// 但需要注意缓冲区溢出等安全问题

public class SecureUsbTransfer {
    
    // 限制单次传输大小，防止内存问题
    private static final int MAX_TRANSFER_SIZE = 64 * 1024; // 64KB
    
    public boolean sendData(UsbDeviceConnection conn, UsbEndpoint endpoint, 
                           byte[] data) {
        // 验证数据大小
        if (data == null || data.length > MAX_TRANSFER_SIZE * 10) {
            return false;
        }
        
        // 分块传输
        int offset = 0;
        while (offset < data.length) {
            int length = Math.min(MAX_TRANSFER_SIZE, data.length - offset);
            int written = conn.bulkTransfer(endpoint, data, offset, length, 5000);
            if (written < 0) {
                return false;
            }
            offset += written;
        }
        return true;
    }
}
```

---

## 7. 推荐架构方案

### 7.1 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        应用层 (App Layer)                        │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐  │
│  │ 打印预览界面  │  │ 打印机设置    │  │ 打印队列管理          │  │
│  └──────────────┘  └──────────────┘  └──────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────────┐
│                      Android打印框架层                           │
│              PrintManager / PrintDocumentAdapter                 │
└─────────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────────┐
│                    cPrint USB打印服务层                          │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │              UsbPrintService (PrintService)               │  │
│  └──────────────────────────────────────────────────────────┘  │
│                              │                                   │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐  │
│  │ 打印机发现   │  │ 打印任务处理 │  │    打印机状态管理        │  │
│  │Discovery    │  │  Job Handler │  │    Status Manager       │  │
│  └─────────────┘  └─────────────┘  └─────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────────┐
│                      设备抽象层 (HAL)                            │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐  │
│  │ USB设备管理  │  │ 协议适配器   │  │    PDL生成器            │  │
│  │ UsbManager  │  │ Protocol    │  │    PDL Generator        │  │
│  │ Wrapper     │  │ Adapter     │  │                         │  │
│  └─────────────┘  └─────────────┘  └─────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────────┐
│                      协议实现层                                  │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐   │
│  │  PCL5   │ │ PCL6/XL │ │PostScript│ │ ESC/P   │ │ ESC/P-R │   │
│  │ Driver  │ │ Driver  │ │ Driver  │ │ Driver  │ │ Driver  │   │
│  └─────────┘ └─────────┘ └─────────┘ └─────────┘ └─────────┘   │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐   │
│  │  SPL    │ │   UFR   │ │  IPP    │ │  GDI    │ │  ZJS    │   │
│  │ Driver  │ │ Driver  │ │ Client  │ │ Driver  │ │ Driver  │   │
│  └─────────┘ └─────────┘ └─────────┘ └─────────┘ └─────────┘   │
└─────────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────────┐
│                    Android USB Host API                          │
│         UsbManager → UsbDevice → UsbDeviceConnection            │
└─────────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────────┐
│                      USB硬件层                                   │
│              USB Type-C Port ←→ USB Printer                     │
└─────────────────────────────────────────────────────────────────┘
```

### 7.2 核心模块设计

#### 7.2.1 USB设备管理器

```java
public class UsbDeviceManager {
    
    private UsbManager usbManager;
    private Map<String, UsbDeviceConnection> connections;
    
    // 查找所有打印机
    public List<UsbPrinterInfo> findPrinters() {
        List<UsbPrinterInfo> printers = new ArrayList<>();
        HashMap<String, UsbDevice> devices = usbManager.getDeviceList();
        
        for (Map.Entry<String, UsbDevice> entry : devices.entrySet()) {
            if (PrinterClassifier.isPrinter(entry.getValue())) {
                printers.add(createPrinterInfo(entry.getValue()));
            }
        }
        return printers;
    }
    
    // 建立连接
    public UsbConnection connect(UsbDevice device) throws UsbException {
        if (!usbManager.hasPermission(device)) {
            throw new UsbException("No USB permission");
        }
        
        UsbInterface intf = findPrinterInterface(device);
        UsbDeviceConnection connection = usbManager.openDevice(device);
        
        if (connection == null) {
            throw new UsbException("Failed to open device");
        }
        
        if (!connection.claimInterface(intf, true)) {
            connection.close();
            throw new UsbException("Failed to claim interface");
        }
        
        return new UsbConnection(connection, intf);
    }
}
```

#### 7.2.2 协议适配器

```java
public interface PrinterDriver {
    byte[] generatePrintJob(PrintDocument document, PrintAttributes attributes);
    PrinterCapabilities getCapabilities();
    PrinterStatus queryStatus();
}

public class DriverFactory {
    
    public static PrinterDriver createDriver(UsbDevice device) {
        PDLType pdl = detectPDL(device);
        
        switch (pdl) {
            case PCL5:
                return new PCL5Driver(device);
            case PCL6_XL:
                return new PCL6XLDriver(device);
            case ESCP:
                return new ESCPDriver(device);
            case ESCPR:
                return new ESCPRDriver(device);
            case POSTSCRIPT:
                return new PostScriptDriver(device);
            default:
                return new GenericPCL5Driver(device);
        }
    }
}
```

#### 7.2.3 PDL生成器

```java
public class PDLGenerator {
    
    // 将PDF转换为打印机PDL
    public byte[] convertPdfToPDL(InputStream pdfStream, PDLType targetPDL,
                                   PrintAttributes attributes) {
        switch (targetPDL) {
            case PCL5:
                return convertToPCL5(pdfStream, attributes);
            case PCL6_XL:
                return convertToPCL6(pdfStream, attributes);
            case POSTSCRIPT:
                return convertToPostScript(pdfStream, attributes);
            case ESCPR:
                return convertToESCPR(pdfStream, attributes);
            default:
                // 使用PDF直接打印（需要打印机支持）
                return readAllBytes(pdfStream);
        }
    }
    
    // PCL5转换
    private byte[] convertToPCL5(InputStream pdf, PrintAttributes attrs) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        
        // PCL5头
        out.write(0x1B); out.write('E');  // 复位
        
        // 页面设置
        if (attrs.getMediaSize().equals(PrintAttributes.MediaSize.ISO_A4)) {
            out.write(0x1B); out.write('&'); out.write('l'); 
            out.write('2'); out.write('6'); out.write('A');  // A4纸张
        }
        
        // 使用PDF渲染器生成光栅图像，然后编码为PCL
        Bitmap pageBitmap = renderPdfPage(pdf, 0);
        byte[] rasterData = encodeBitmapToPCLRaster(pageBitmap);
        out.write(rasterData, 0, rasterData.length);
        
        // PCL5尾
        out.write(0x1B); out.write('E');  // 复位
        
        return out.toByteArray();
    }
}
```

---

## 8. 关键代码示例

### 8.1 USB打印机连接完整示例

```java
public class UsbPrinterConnection {
    
    private static final int TRANSFER_TIMEOUT = 5000;
    
    private UsbManager usbManager;
    private UsbDevice device;
    private UsbDeviceConnection connection;
    private UsbInterface printerInterface;
    private UsbEndpoint outEndpoint;
    private UsbEndpoint inEndpoint;
    
    public UsbPrinterConnection(Context context, UsbDevice device) {
        this.usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        this.device = device;
    }
    
    public boolean connect() {
        if (!usbManager.hasPermission(device)) {
            return false;
        }
        
        // 查找打印机接口
        printerInterface = findPrinterInterface(device);
        if (printerInterface == null) {
            return false;
        }
        
        // 查找端点
        for (int i = 0; i < printerInterface.getEndpointCount(); i++) {
            UsbEndpoint ep = printerInterface.getEndpoint(i);
            if (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (ep.getDirection() == UsbConstants.USB_DIR_OUT) {
                    outEndpoint = ep;
                } else if (ep.getDirection() == UsbConstants.USB_DIR_IN) {
                    inEndpoint = ep;
                }
            }
        }
        
        if (outEndpoint == null) {
            return false;
        }
        
        // 打开连接
        connection = usbManager.openDevice(device);
        if (connection == null) {
            return false;
        }
        
        // 声明接口
        if (!connection.claimInterface(printerInterface, true)) {
            connection.close();
            return false;
        }
        
        return true;
    }
    
    public void disconnect() {
        if (connection != null) {
            connection.releaseInterface(printerInterface);
            connection.close();
            connection = null;
        }
    }
    
    public boolean sendData(byte[] data) {
        if (connection == null || outEndpoint == null) {
            return false;
        }
        
        int offset = 0;
        while (offset < data.length) {
            int length = Math.min(outEndpoint.getMaxPacketSize(), data.length - offset);
            int written = connection.bulkTransfer(outEndpoint, data, offset, length, TRANSFER_TIMEOUT);
            if (written < 0) {
                return false;
            }
            offset += written;
        }
        return true;
    }
    
    public byte[] receiveData(int length) {
        if (connection == null || inEndpoint == null) {
            return null;
        }
        
        byte[] buffer = new byte[length];
        int received = connection.bulkTransfer(inEndpoint, buffer, length, TRANSFER_TIMEOUT);
        if (received < 0) {
            return null;
        }
        
        return Arrays.copyOf(buffer, received);
    }
    
    // 获取IEEE 1284设备ID
    public String getDeviceId() {
        byte[] buffer = new byte[256];
        int length = connection.controlTransfer(
            UsbConstants.USB_DIR_IN | UsbConstants.USB_TYPE_CLASS | UsbConstants.USB_INTERFACE_SUBCLASS_BOOT,
            0x00,  // GET_DEVICE_ID
            0,
            printerInterface.getId(),
            buffer,
            buffer.length,
            TRANSFER_TIMEOUT
        );
        
        if (length > 2) {
            // 前两个字节是长度
            return new String(buffer, 2, length - 2, StandardCharsets.UTF_8);
        }
        return null;
    }
    
    // 获取打印机状态
    public PrinterStatus getStatus() {
        byte[] buffer = new byte[1];
        int length = connection.controlTransfer(
            UsbConstants.USB_DIR_IN | UsbConstants.USB_TYPE_CLASS,
            0x01,  // GET_PORT_STATUS
            0,
            printerInterface.getId(),
            buffer,
            buffer.length,
            TRANSFER_TIMEOUT
        );
        
        if (length == 1) {
            return PrinterStatus.fromByte(buffer[0]);
        }
        return PrinterStatus.UNKNOWN;
    }
    
    private UsbInterface findPrinterInterface(UsbDevice device) {
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface intf = device.getInterface(i);
            if (intf.getInterfaceClass() == UsbConstants.USB_CLASS_PRINTER) {
                return intf;
            }
        }
        return null;
    }
}
```

### 8.2 PCL5驱动实现

```java
public class PCL5Driver implements PrinterDriver {
    
    private static final byte ESC = 0x1B;
    private static final byte FS = 0x1C;
    
    @Override
    public byte[] generatePrintJob(PrintDocument document, PrintAttributes attributes) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        
        try {
            // 初始化打印机
            writeCommand(out, ESC, 'E');  // 复位
            writeCommand(out, ESC, '%', '0', 'B');  // PCL5模式
            
            // 页面设置
            setupPage(out, attributes);
            
            // 处理文档内容
            for (int page = 0; page < document.getPageCount(); page++) {
                if (page > 0) {
                    writeCommand(out, ESC, '&', 'l', '0', 'H');  // 进纸
                }
                
                writePage(out, document.getPage(page), attributes);
            }
            
            // 结束打印
            writeCommand(out, ESC, 'E');  // 复位
            
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        return out.toByteArray();
    }
    
    private void setupPage(ByteArrayOutputStream out, PrintAttributes attrs) throws IOException {
        // 纸张大小
        PrintAttributes.MediaSize size = attrs.getMediaSize();
        if (size.equals(PrintAttributes.MediaSize.ISO_A4)) {
            writeCommand(out, ESC, '&', 'l', '2', '6', 'A');
        } else if (size.equals(PrintAttributes.MediaSize.ISO_A3)) {
            writeCommand(out, ESC, '&', 'l', '2', '7', 'A');
        } else if (size.equals(PrintAttributes.MediaSize.NA_LETTER)) {
            writeCommand(out, ESC, '&', 'l', '2', 'A');
        }
        
        // 方向
        if (attrs.getMediaSize().isPortrait()) {
            writeCommand(out, ESC, '&', 'l', '0', 'O');
        } else {
            writeCommand(out, ESC, '&', 'l', '1', 'O');
        }
        
        // 分辨率
        writeCommand(out, ESC, '*', 't', '3', '0', '0', 'R');  // 300 DPI
    }
    
    private void writePage(ByteArrayOutputStream out, Page page, 
                          PrintAttributes attrs) throws IOException {
        // 这里简化处理，实际应该渲染PDF/图片为光栅数据
        // 然后使用PCL光栅图形命令输出
        
        // 示例：输出文本
        writeCommand(out, ESC, '(', 's', '1', '2', 'H');  // 12pt字体
        writeCommand(out, ESC, '*', 'p', '1', '0', '0', 'X');  // X位置
        writeCommand(out, ESC, '*', 'p', '1', '0', '0', 'Y');  // Y位置
        out.write("Sample Text".getBytes());
    }
    
    private void writeCommand(ByteArrayOutputStream out, byte... bytes) throws IOException {
        out.write(bytes);
    }
    
    private void writeCommand(ByteArrayOutputStream out, byte prefix, char... chars) throws IOException {
        out.write(prefix);
        for (char c : chars) {
            out.write(c);
        }
    }
    
    @Override
    public PrinterCapabilities getCapabilities() {
        return new PrinterCapabilities.Builder()
            .addMediaSize(PrintAttributes.MediaSize.ISO_A4, true)
            .addMediaSize(PrintAttributes.MediaSize.ISO_A3, false)
            .addMediaSize(PrintAttributes.MediaSize.NA_LETTER, false)
            .addResolution(new PrintAttributes.Resolution("300dpi", "300 DPI", 300, 300), true)
            .addResolution(new PrintAttributes.Resolution("600dpi", "600 DPI", 600, 600), false)
            .setColorModes(PrintAttributes.COLOR_MODE_COLOR | PrintAttributes.COLOR_MODE_MONOCHROME, 
                          PrintAttributes.COLOR_MODE_MONOCHROME)
            .build();
    }
    
    @Override
    public PrinterStatus queryStatus() {
        return PrinterStatus.IDLE;
    }
}
```

### 8.3 ESC/P驱动实现

```java
public class ESCPDriver implements PrinterDriver {
    
    private static final byte ESC = 0x1B;
    private static final byte GS = 0x1D;
    private static final byte LF = 0x0A;
    private static final byte CR = 0x0D;
    private static final byte FF = 0x0C;
    
    @Override
    public byte[] generatePrintJob(PrintDocument document, PrintAttributes attributes) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        
        try {
            // 初始化
            out.write(ESC); out.write('@');  // 初始化
            
            // 设置字符编码
            out.write(ESC); out.write('t'); out.write(0);  // PC437
            
            // 页面设置
            setupPage(out, attributes);
            
            // 处理页面
            for (int i = 0; i < document.getPageCount(); i++) {
                if (i > 0) {
                    out.write(FF);  // 换页
                }
                writePage(out, document.getPage(i));
            }
            
            // 切纸（针式打印机）
            out.write(ESC); out.write('i');  // 全切
            
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        return out.toByteArray();
    }
    
    private void setupPage(ByteArrayOutputStream out, PrintAttributes attrs) throws IOException {
        // 设置行间距
        out.write(ESC); out.write('2');  // 1/6英寸行间距
        
        // 设置页长
        out.write(ESC); out.write('C'); out.write(66);  // 66行/页
        
        // 设置左边距
        out.write(ESC); out.write('l'); out.write(0);  // 0字符
        
        // 设置右边距
        out.write(ESC); out.write('Q'); out.write(80);  // 80字符
    }
    
    private void writePage(ByteArrayOutputStream out, Page page) throws IOException {
        // 设置字体
        out.write(ESC); out.write('M'); out.write(0);  // 12 CPI
        
        // 示例文本输出
        String text = "ESC/P Print Sample\r\n";
        out.write(text.getBytes("IBM437"));
        
        // 粗体
        out.write(ESC); out.write('E');
        out.write("Bold Text\r\n".getBytes("IBM437"));
        out.write(ESC); out.write('F');
        
        // 倍高倍宽
        out.write(ESC); out.write('W'); out.write(1);
        out.write("Large Text\r\n".getBytes("IBM437"));
        out.write(ESC); out.write('W'); out.write(0);
    }
    
    @Override
    public PrinterCapabilities getCapabilities() {
        return new PrinterCapabilities.Builder()
            .addMediaSize(PrintAttributes.MediaSize.ISO_A4, true)
            .setColorModes(PrintAttributes.COLOR_MODE_MONOCHROME, 
                          PrintAttributes.COLOR_MODE_MONOCHROME)
            .build();
    }
    
    @Override
    public PrinterStatus queryStatus() {
        return PrinterStatus.IDLE;
    }
}
```

### 8.4 PrintService完整实现

```java
public class CPrintUsbService extends PrintService {
    
    private static final String TAG = "CPrintUsbService";
    
    private UsbManager usbManager;
    private UsbDeviceReceiver usbReceiver;
    private Map<PrinterId, UsbDevice> printerDevices = new HashMap<>();
    
    @Override
    public void onCreate() {
        super.onCreate();
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        registerUsbReceiver();
    }
    
    @Override
    public void onDestroy() {
        unregisterUsbReceiver();
        super.onDestroy();
    }
    
    @Override
    protected PrinterDiscoverySession onCreatePrinterDiscoverySession() {
        return new CPrintDiscoverySession();
    }
    
    @Override
    protected void onPrintJobQueued(PrintJob printJob) {
        new Thread(() -> handlePrintJob(printJob)).start();
    }
    
    @Override
    protected void onRequestCancelPrintJob(PrintJob printJob) {
        printJob.cancel();
    }
    
    private void handlePrintJob(PrintJob printJob) {
        PrintJobInfo jobInfo = printJob.getInfo();
        PrinterId printerId = jobInfo.getPrinterId();
        UsbDevice device = printerDevices.get(printerId);
        
        if (device == null) {
            printJob.fail("Printer not found");
            return;
        }
        
        printJob.start();
        
        try {
            // 建立USB连接
            UsbPrinterConnection connection = new UsbPrinterConnection(this, device);
            if (!connection.connect()) {
                printJob.fail("Failed to connect to printer");
                return;
            }
            
            // 获取打印数据
            PrintDocument document = printJob.getDocument();
            ParcelFileDescriptor pdfFile = document.getData();
            
            // 生成PDL数据
            PrinterDriver driver = DriverFactory.createDriver(device);
            byte[] printData = driver.generatePrintJob(
                loadDocument(pdfFile), 
                jobInfo.getAttributes()
            );
            
            // 发送数据
            if (connection.sendData(printData)) {
                printJob.complete();
            } else {
                printJob.fail("Failed to send print data");
            }
            
            connection.disconnect();
            
        } catch (Exception e) {
            Log.e(TAG, "Print job failed", e);
            printJob.fail(e.getMessage());
        }
    }
    
    private class CPrintDiscoverySession extends PrinterDiscoverySession {
        
        @Override
        public void onStartPrinterDiscovery(List<PrinterId> priorityList) {
            discoverUsbPrinters();
        }
        
        @Override
        public void onStopPrinterDiscovery() {
            // 停止发现
        }
        
        @Override
        public void onValidatePrinters(List<PrinterId> printerIds) {
            // 验证打印机状态
        }
        
        @Override
        public void onStartPrinterStateTracking(PrinterId printerId) {
            // 开始状态跟踪
        }
        
        @Override
        public void onStopPrinterStateTracking(PrinterId printerId) {
            // 停止状态跟踪
        }
        
        @Override
        public void onDestroy() {
            printerDevices.clear();
        }
        
        private void discoverUsbPrinters() {
            HashMap<String, UsbDevice> devices = usbManager.getDeviceList();
            List<PrinterInfo> printers = new ArrayList<>();
            
            for (Map.Entry<String, UsbDevice> entry : devices.entrySet()) {
                UsbDevice device = entry.getValue();
                if (isPrinter(device)) {
                    PrinterId printerId = generatePrinterId(device);
                    printerDevices.put(printerId, device);
                    
                    PrinterInfo printer = new PrinterInfo.Builder(
                        printerId,
                        getPrinterName(device),
                        PrinterInfo.STATUS_IDLE
                    )
                    .setDescription(device.getProductName())
                    .setCapabilities(queryCapabilities(device))
                    .build();
                    
                    printers.add(printer);
                }
            }
            
            addPrinters(printers);
        }
    }
    
    private void registerUsbReceiver() {
        usbReceiver = new UsbDeviceReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(usbReceiver, filter);
    }
    
    private void unregisterUsbReceiver() {
        if (usbReceiver != null) {
            unregisterReceiver(usbReceiver);
        }
    }
    
    private boolean isPrinter(UsbDevice device) {
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            if (device.getInterface(i).getInterfaceClass() == 
                UsbConstants.USB_CLASS_PRINTER) {
                return true;
            }
        }
        return false;
    }
    
    private PrinterId generatePrinterId(UsbDevice device) {
        return generateGlobalPrinterId(
            "usb://" + device.getVendorId() + "/" + device.getProductId() + 
            "/" + device.getSerialNumber()
        );
    }
    
    private String getPrinterName(UsbDevice device) {
        String name = device.getProductName();
        if (name == null || name.isEmpty()) {
            name = PrinterDatabase.getName(device.getVendorId(), device.getProductId());
        }
        if (name == null) {
            name = String.format("USB Printer (%04X:%04X)", 
                device.getVendorId(), device.getProductId());
        }
        return name;
    }
    
    private PrinterCapabilities queryCapabilities(UsbDevice device) {
        // 从数据库或设备查询能力
        return PrinterDatabase.getCapabilities(device.getVendorId(), device.getProductId());
    }
    
    private PrintDocument loadDocument(ParcelFileDescriptor pfd) {
        // 加载PDF文档
        return new PrintDocument(pfd);
    }
}
```

---

## 9. 兼容性问题和解决方案

### 9.1 常见问题列表

| 问题 | 原因 | 解决方案 |
|------|------|----------|
| USB权限被拒绝 | Android 12+权限变化 | 使用显式PendingIntent |
| 打印机无响应 | 协议不匹配 | 实现多种PDL驱动 |
| 打印乱码 | 字符编码问题 | 统一使用UTF-8或指定编码 |
| 大文件打印失败 | 内存限制 | 分块传输，流式处理 |
| 打印机状态获取失败 | 双向通信不支持 | 降级到单向模式 |
| 部分页面缺失 | 分页处理错误 | 正确处理FF换页符 |
| 颜色打印异常 | PCL颜色空间设置错误 | 正确设置RGB/CMYK |

### 9.2 设备特定处理

```java
public class DeviceSpecificHandler {
    
    // HP打印机特殊处理
    public static byte[] hpPreProcess(int productId, byte[] data) {
        // HP LaserJet 10xx系列需要特殊初始化
        if (isLaserJet10xx(productId)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            // 发送PJL头
            out.write(0x1B); out.write('%'); out.write('-'); out.write('1'); 
            out.write('2'); out.write('3'); out.write('4'); out.write('5'); 
            out.write('X'); out.write('@'); out.write('P'); out.write('J'); 
            out.write('L'); out.write('\n');
            out.write('@'); out.write('P'); out.write('J'); out.write('L'); 
            out.write(' '); out.write('E'); out.write('N'); out.write('T'); 
            out.write('E'); out.write('R'); out.write(' '); out.write('L'); 
            out.write('A'); out.write('N'); out.write('G'); out.write('U'); 
            out.write('A'); out.write('G'); out.write('E'); out.write('='); 
            out.write('P'); out.write('C'); out.write('L'); out.write('\n');
            try {
                out.write(data);
            } catch (IOException e) {
                e.printStackTrace();
            }
            out.write(0x1B); out.write('%'); out.write('-'); out.write('1'); 
            out.write('2'); out.write('3'); out.write('4'); out.write('5'); 
            out.write('X');
            return out.toByteArray();
        }
        return data;
    }
    
    // Epson打印机特殊处理
    public static byte[] epsonPreProcess(int productId, byte[] data) {
        // ESC/P-R打印机需要头信息
        if (isESCPR(productId)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            // ESC/P-R头
            out.write(0x00); out.write(0x00); out.write(0x00);  // 魔数
            out.write(0x1B); out.write(0x01);  // 命令开始
            // ...
            try {
                out.write(data);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return out.toByteArray();
        }
        return data;
    }
    
    // Canon打印机特殊处理
    public static byte[] canonPreProcess(int productId, byte[] data) {
        // Canon UFR需要特殊处理
        if (isUFR(productId)) {
            // UFR是专有格式，需要转换
            return convertToUFR(data);
        }
        return data;
    }
}
```

### 9.3 降级策略

```java
public class FallbackStrategy {
    
    public byte[] generateWithFallback(UsbDevice device, PrintDocument doc, 
                                       PrintAttributes attrs) {
        List<PDLType> pdlList = getSupportedPDLs(device);
        
        for (PDLType pdl : pdlList) {
            try {
                PrinterDriver driver = DriverFactory.createDriver(pdl);
                return driver.generatePrintJob(doc, attrs);
            } catch (Exception e) {
                Log.w(TAG, "PDL " + pdl + " failed, trying next");
            }
        }
        
        // 最后尝试通用PCL5
        return new GenericPCL5Driver(device).generatePrintJob(doc, attrs);
    }
    
    private List<PDLType> getSupportedPDLs(UsbDevice device) {
        List<PDLType> pdls = new ArrayList<>();
        
        // 从IEEE 1284设备ID解析
        String deviceId = getDeviceId(device);
        if (deviceId != null) {
            if (deviceId.contains("PCL5")) pdls.add(PDLType.PCL5);
            if (deviceId.contains("PCL6")) pdls.add(PDLType.PCL6_XL);
            if (deviceId.contains("PCLXL")) pdls.add(PDLType.PCL6_XL);
            if (deviceId.contains("POSTSCRIPT")) pdls.add(PDLType.POSTSCRIPT);
            if (deviceId.contains("ESCP")) pdls.add(PDLType.ESCP);
        }
        
        // 根据厂商添加默认PDL
        int vid = device.getVendorId();
        if (vid == 0x03F0 && !pdls.contains(PDLType.PCL5)) {
            pdls.add(PDLType.PCL5);  // HP默认PCL5
        } else if (vid == 0x04B8 && !pdls.contains(PDLType.ESCP)) {
            pdls.add(PDLType.ESCP);  // Epson默认ESC/P
        }
        
        return pdls;
    }
}
```

---

## 10. 依赖库列表

### 10.1 必需依赖

```gradle
dependencies {
    // AndroidX Core
    implementation 'androidx.core:core:1.12.0'
    
    // Print Service Support
    implementation 'androidx.print:print:1.1.0-beta01'
    
    // PDF处理 (用于PDF渲染)
    implementation 'com.itextpdf:itext7-core:8.0.2'
    
    // 或者使用Android原生PdfRenderer
    // 无需额外依赖
    
    // 图片处理
    implementation 'androidx.exifinterface:exifinterface:1.3.6'
}
```

### 10.2 可选依赖

```gradle
dependencies {
    // IPP协议实现
    implementation 'org.jipp:jipp-core:0.7.15'
    
    // CUPS客户端 (如果需要IPP-over-USB)
    implementation 'org.cups4j:cups4j:0.7.8'
    
    // 日志
    implementation 'com.jakewharton.timber:timber:5.0.1'
    
    // 协程 (用于异步处理)
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
    
    // JSON处理
    implementation 'com.google.code.gson:gson:2.10.1'
}
```

### 10.3 NDK依赖 (可选)

```cmake
# CMakeLists.txt - 如果需要libusb
find_package(PkgConfig)
pkg_check_modules(LIBUSB REQUIRED libusb-1.0)

add_library(usb-print SHARED
    src/main/cpp/usb_backend.c
    src/main/cpp/pcl_generator.c
)

target_include_directories(usb-print PRIVATE
    ${LIBUSB_INCLUDE_DIRS}
)

target_link_libraries(usb-print
    ${LIBUSB_LIBRARIES}
    android
    log
)
```

### 10.4 推荐开源项目参考

| 项目 | 用途 | 许可证 |
|------|------|--------|
| [cups4j](https://github.com/harwey/cups4j) | Java CUPS客户端 | LGPL |
| [jipp](https://github.com/HPInc/jipp) | Java IPP实现 | MIT |
| [libusb](https://github.com/libusb/libusb) | USB访问库 | LGPL |
| [Ghostscript](https://github.com/ArtifexSoftware/ghostpdl) | PostScript/PDF处理 | AGPL |
| [pdfium](https://pdfium.googlesource.com/pdfium/) | PDF渲染 | BSD-3 |

---

## 附录

### A. USB打印机类规范参考

- USB Device Class Definition for Printing Devices v1.1
- IEEE 1284-2000 Standard for Signaling Method
- PCL 5 Printer Language Technical Reference
- PCL XL Feature Reference Protocol Class 2.1
- Adobe PostScript Language Reference v3
- Epson ESC/P Reference Manual

### B. Android开发参考

- Android USB Host API Guide
- Android Print Framework Documentation
- Android NDK Guide

### C. 厂商开发资源

- HP Printer Language Technical Reference
- Epson ESC/P Command Reference
- Canon Printer Driver Development Kit
- Brother Printer Technical Reference Guide

---

*报告版本: 1.0*
*更新日期: 2026-04-11*
*作者: cPrint技术团队*
