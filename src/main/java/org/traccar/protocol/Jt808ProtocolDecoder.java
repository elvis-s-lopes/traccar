package org.traccar.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import org.traccar.BaseProtocolDecoder;
import org.traccar.NetworkMessage;
import org.traccar.Protocol;
import org.traccar.helper.BcdUtil;
import org.traccar.helper.BitUtil;
import org.traccar.helper.Checksum;
import org.traccar.helper.DateBuilder;
import org.traccar.helper.UnitsConverter;
import org.traccar.model.CellTower;
import org.traccar.model.Network;
import org.traccar.model.Position;
import org.traccar.session.DeviceSession;

import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.TimeZone;

public class Jt808ProtocolDecoder extends BaseProtocolDecoder {
  public static final int MSG_GENERAL_RESPONSE = 32769;
  
  public static final int MSG_GENERAL_RESPONSE_2 = 17409;
  
  public static final int MSG_HEARTBEAT = 2;
  
  public static final int MSG_TERMINAL_REGISTER = 256;
  
  public static final int MSG_TERMINAL_REGISTER_RESPONSE = 33024;
  
  public static final int MSG_TERMINAL_AUTH = 258;
  
  public static final int MSG_LOCATION_REPORT = 512;
  
  public static final int MSG_ACCELERATION = 8304;
  
  public static final int MSG_LOCATION_REPORT_2 = 21761;
  
  public static final int MSG_LOCATION_REPORT_BLIND = 21762;
  
  public static final int MSG_LOCATION_BATCH = 1796;
  
  public static final int MSG_TERMINAL_CONTROL = 33029;
  
  public static final int MSG_OIL_CONTROL = 40966;
  
  public static final int MSG_OIL_CONTROL_PRORRAC = 34048;
  
  public static final int MSG_TIME_SYNC_REQUEST = 265;
  
  public static final int MSG_TIME_SYNC_RESPONSE = 33033;
  
  public static final int MSG_PHOTO = 34952;
  
  public static final int MSG_TRANSPARENT = 2304;
  
  public static final int RESULT_SUCCESS = 0;
  
  public Jt808ProtocolDecoder(Protocol protocol) {
    super(protocol);
  }
  
  private static double convertCoordinate(int raw) {
    int degrees = raw / 1000000;
    double minutes = (raw % 1000000) / 10000.0D;
    return degrees + minutes / 60.0D;
  }
  
  public static ByteBuf formatMessage(int type, ByteBuf id, boolean shortIndex, ByteBuf data) {
    ByteBuf buf = Unpooled.buffer();
    buf.writeByte(126);
    buf.writeShort(type);
    buf.writeShort(data.readableBytes());
    buf.writeBytes(id);
    if (shortIndex) {
      buf.writeByte(1);
    } else {
      buf.writeShort(0);
    } 
    buf.writeBytes(data);
    data.release();
    buf.writeByte(Checksum.xor(buf.nioBuffer(1, buf.readableBytes() - 1)));
    buf.writeByte(126);
    return buf;
  }
  
  static void decodeBinaryLocation(ByteBuf buf, Position position) {
    DateBuilder dateBuilder = (new DateBuilder()).setDay(BcdUtil.readInteger(buf, 2)).setMonth(BcdUtil.readInteger(buf, 2)).setYear(BcdUtil.readInteger(buf, 2)).setHour(BcdUtil.readInteger(buf, 2)).setMinute(BcdUtil.readInteger(buf, 2)).setSecond(BcdUtil.readInteger(buf, 2));
    position.setTime(dateBuilder.getDate());
    double latitude = convertCoordinate(BcdUtil.readInteger(buf, 8));
    double longitude = convertCoordinate(BcdUtil.readInteger(buf, 9));
    byte flags = buf.readByte();
    position.setValid(((flags & 0x1) == 1));
    if ((flags & 0x2) == 0)
      latitude = -latitude; 
    position.setLatitude(latitude);
    if ((flags & 0x4) == 0)
      longitude = -longitude; 
    position.setLongitude(longitude);
    position.setSpeed(BcdUtil.readInteger(buf, 2));
    position.setCourse(buf.readUnsignedByte() * 2.0D);
  }
  
  private void sendGeneralResponse(Channel channel, SocketAddress remoteAddress, ByteBuf id, int type, int index) {
    if (channel != null) {
      ByteBuf response = Unpooled.buffer();
      response.writeShort(index);
      response.writeShort(type);
      response.writeByte(0);
      channel.writeAndFlush(new NetworkMessage(
            formatMessage(32769, id, false, response), remoteAddress));
    } 
  }
  
  private void sendGeneralResponse2(Channel channel, SocketAddress remoteAddress, ByteBuf id, int type) {
    if (channel != null) {
      ByteBuf response = Unpooled.buffer();
      response.writeShort(type);
      response.writeByte(0);
      channel.writeAndFlush(new NetworkMessage(
            formatMessage(17409, id, true, response), remoteAddress));
    } 
  }
  
  private String decodeAlarm(long value) {
    if (BitUtil.check(value, 0))
      return "sos"; 
    if (BitUtil.check(value, 1))
      return "overspeed"; 
    if (BitUtil.check(value, 5))
      return "gpsAntennaCut"; 
    if (BitUtil.check(value, 4) || BitUtil.check(value, 9) || 
      BitUtil.check(value, 10) || BitUtil.check(value, 11))
      return "fault"; 
    if (BitUtil.check(value, 8))
      return "powerCut"; 
    if (BitUtil.check(value, 7))
      return "lowBattery"; 
    if (BitUtil.check(value, 8))
      return "powerOff"; 
    if (BitUtil.check(value, 17))
      return "tampering"; 
    if (BitUtil.check(value, 20))
      return "geofence"; 
    if (BitUtil.check(value, 28))
      return "movement"; 
    if (BitUtil.check(value, 29))
      return "accident"; 
    return null;
  }
  
  private int readSignedWord(ByteBuf buf) {
    int value = buf.readUnsignedShort();
    return BitUtil.check(value, 15) ? -BitUtil.to(value, 15) : BitUtil.to(value, 15);
  }
  
  private Date readDate(ByteBuf buf, TimeZone timeZone) {
    DateBuilder dateBuilder = (new DateBuilder(timeZone)).setYear(BcdUtil.readInteger(buf, 2)).setMonth(BcdUtil.readInteger(buf, 2)).setDay(BcdUtil.readInteger(buf, 2)).setHour(BcdUtil.readInteger(buf, 2)).setMinute(BcdUtil.readInteger(buf, 2)).setSecond(BcdUtil.readInteger(buf, 2));
    return dateBuilder.getDate();
  }
  
  @Override
  protected Object decode(Channel channel, SocketAddress remoteAddress, Object msg) throws Exception {
    int index;
    ByteBuf buf = (ByteBuf) msg;
    if (buf.getByte(buf.readerIndex()) == 40) {
      String sentence = buf.toString(StandardCharsets.US_ASCII);
      if (sentence.contains("BASE,2")) {
        DateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        String response = sentence.replace("TIME", dateFormat.format(new Date()));
        if (channel != null)
          channel.writeAndFlush(new NetworkMessage(
                Unpooled.copiedBuffer(response, StandardCharsets.US_ASCII), remoteAddress));
        return null;
      }
      return decodeResult(channel, remoteAddress, sentence);
    }
    buf.readUnsignedByte();
    int type = buf.readUnsignedShort();
    int attribute = buf.readUnsignedShort();
    ByteBuf id = buf.readSlice(6);
    if (type == 21761 || type == 21762) {
      index = buf.readUnsignedByte();
    } else {
      index = buf.readUnsignedShort();
    }
    DeviceSession deviceSession = getDeviceSession(channel, remoteAddress, ByteBufUtil.hexDump(id));
    if (deviceSession == null)
      return null;

    if (type == 256) {
      if (channel != null) {
        ByteBuf response = Unpooled.buffer();
        response.writeShort(index);
        response.writeByte(0);
        response.writeBytes(ByteBufUtil.hexDump(id).getBytes(StandardCharsets.US_ASCII));
        channel.writeAndFlush(new NetworkMessage(
              formatMessage(MSG_TERMINAL_REGISTER_RESPONSE, id, false, response), remoteAddress));
      }
    } else if (type == 258 || type == 2 || type == 34952) {
      sendGeneralResponse(channel, remoteAddress, id, type, index);
    } else {
      if (type == 512) {
        sendGeneralResponse(channel, remoteAddress, id, type, index);
        return decodeLocation(deviceSession, buf);
      }
      if (type == 21761 || type == 21762) {
        if (BitUtil.check(attribute, 15))
          sendGeneralResponse2(channel, remoteAddress, id, type);
        return decodeLocation2(deviceSession, buf, type);
      }
      if (type == 1796) {
        sendGeneralResponse(channel, remoteAddress, id, type, index);
        return decodeLocationBatch(deviceSession, buf);
      }
      if (type == 265) {
        if (channel != null) {
          Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
          ByteBuf response = Unpooled.buffer();
          response.writeShort(calendar.get(1));
          response.writeByte(calendar.get(2) + 1);
          response.writeByte(calendar.get(5));
          response.writeByte(calendar.get(11));
          response.writeByte(calendar.get(12));
          response.writeByte(calendar.get(13));
          channel.writeAndFlush(new NetworkMessage(
                formatMessage(MSG_TERMINAL_REGISTER_RESPONSE, id, false, response), remoteAddress));
        }
      } else {
        if (type == 8304) {
          Position position = new Position(getProtocolName());
          position.setDeviceId(deviceSession.getDeviceId());
          getLastLocation(position, null);
          StringBuilder data = new StringBuilder("[");
          while (buf.readableBytes() > 2) {
            buf.skipBytes(6);
            if (data.length() > 1)
              data.append(",");
            data.append("[");
            data.append(readSignedWord(buf));
            data.append(",");
            data.append(readSignedWord(buf));
            data.append(",");
            data.append(readSignedWord(buf));
            data.append("]");
          }
          data.append("]");
          position.set("gSensor", data.toString());
          return position;
        }
        if (type == 2304)
          return decodeTransparent(deviceSession, buf);
      }
    }
    return null;
  }
  
  private Position decodeResult(Channel channel, SocketAddress remoteAddress, String sentence) {
    DeviceSession deviceSession = getDeviceSession(channel, remoteAddress);
    if (deviceSession != null) {
      Position position = new Position(getProtocolName());
      position.setDeviceId(deviceSession.getDeviceId());
      getLastLocation(position, null);
      position.set("result", sentence);
      return position;
    }
    return null;
  }
  
  private String decodeOperadora(int mnc) {
    switch (mnc) {
      case 0:
        return null;
      case 1:
        return "SISTEER";
      case 2:
      case 3:
      case 4:
      case 8:
        return "TIM";
      case 5:
      case 38:
        return "CLARO";
      case 6:
      case 10:
      case 11:
      case 23:
        return "VIVO";
      case 16:
      case 24:
      case 30:
      case 31:
        return "OI";
      case 15:
        return "Sercomtel";
      case 17:
        return "Correios";
      case 18:
        return "Datora";
      case 21:
        return "Ligue Telecom";
      case 28:
        return "Roaming Vivo/Claro";
      case 32:
      case 33:
      case 34:
        return "ALGAR";
      case 35:
        return "Telcom";
      case 36:
        return "Options";
      case 37:
        return "Unicel";
      case 39:
        return "Nextel";
      case 54:
        return "Porto Conecta";
    } 
    return null;
  }
  
  private void decodeExtension(Position position, ByteBuf buf, int endIndex) {
    while (buf.readerIndex() < endIndex) {
      String codes;
      int type = buf.readUnsignedByte();
      int length = buf.readUnsignedByte();
      switch (type) {
        case 1:
          position.set("odometer", Long.valueOf(buf.readUnsignedInt() * 100L));
          continue;
        case 2:
          position.set("fuel", Double.valueOf(buf.readUnsignedShort() * 0.1D));
          continue;
        case 3:
          position.set("obdSpeed", Double.valueOf(buf.readUnsignedShort() * 0.1D));
          continue;
        case 128:
          position.set("obdSpeed", Short.valueOf(buf.readUnsignedByte()));
          continue;
        case 129:
          position.set("rpm", Integer.valueOf(buf.readUnsignedShort()));
          continue;
        case 130:
          position.set("power", Double.valueOf(buf.readUnsignedShort() * 0.1D));
          continue;
        case 131:
          position.set("engineLoad", Short.valueOf(buf.readUnsignedByte()));
          continue;
        case 132:
          position.set("coolantTemp", Integer.valueOf(buf.readUnsignedByte() - 40));
          continue;
        case 133:
          position.set("fuelConsumption", Integer.valueOf(buf.readUnsignedShort()));
          continue;
        case 134:
          position.set("intakeTemp", Integer.valueOf(buf.readUnsignedByte() - 40));
          continue;
        case 135:
          position.set("intakeFlow", Integer.valueOf(buf.readUnsignedShort()));
          continue;
        case 136:
          position.set("intakePressure", Short.valueOf(buf.readUnsignedByte()));
          continue;
        case 137:
          position.set("throttle", Short.valueOf(buf.readUnsignedByte()));
          continue;
        case 139:
          position.set("vin", buf.readCharSequence(17, StandardCharsets.US_ASCII).toString());
          continue;
        case 140:
          position.set("obdOdometer", Long.valueOf(buf.readUnsignedInt() * 100L));
          continue;
        case 141:
          position.set("tripOdometer", Long.valueOf(buf.readUnsignedShort() * 1000L));
          continue;
        case 142:
          position.set("fuel", Short.valueOf(buf.readUnsignedByte()));
          continue;
        case 160:
          codes = buf.readCharSequence(length, StandardCharsets.US_ASCII).toString();
          position.set("dtcs", codes.replace(',', ' '));
          continue;
        case 204:
          position.set("iccid", buf.readCharSequence(20, StandardCharsets.US_ASCII).toString());
          continue;
      } 
      buf.skipBytes(length);
    } 
  }
  
  private void decodeCoordinates(Position position, ByteBuf buf) {
    int status = buf.readInt();
    position.set("ignition", Boolean.valueOf(BitUtil.check(status, 0)));
    position.set("blocked", Boolean.valueOf(BitUtil.check(status, 10)));
    if (BitUtil.check(status, 11))
      position.set("alarm", "powerCut"); 
    position.setValid(BitUtil.check(status, 1));
    double lat = buf.readUnsignedInt() * 1.0E-6D;
    double lon = buf.readUnsignedInt() * 1.0E-6D;
    if (BitUtil.check(status, 2)) {
      position.setLatitude(-lat);
    } else {
      position.setLatitude(lat);
    } 
    if (BitUtil.check(status, 3)) {
      position.setLongitude(-lon);
    } else {
      position.setLongitude(lon);
    } 
  }
  
  private Position decodeLocation(DeviceSession deviceSession, ByteBuf buf) {
    Position position = new Position(getProtocolName());
    position.setDeviceId(deviceSession.getDeviceId());
    position.set("alarm", decodeAlarm(buf.readUnsignedInt()));
    decodeCoordinates(position, buf);
    position.setAltitude(buf.readShort());
    position.setSpeed(UnitsConverter.knotsFromKph(buf.readUnsignedShort() * 0.1D));
    position.setCourse(buf.readUnsignedShort());
    position.setTime(readDate(buf, getTimeZone(deviceSession.getDeviceId(), "GMT+8")));
    if (buf.readableBytes() == 20) {
      buf.skipBytes(4);
      position.set("odometer", Long.valueOf(buf.readUnsignedInt() * 1000L));
      position.set("battery", Double.valueOf(buf.readUnsignedShort() * 0.1D));
      buf.readUnsignedInt();
      position.set("rssi", Short.valueOf(buf.readUnsignedByte()));
      buf.skipBytes(3);
      return position;
    } 
    while (buf.readableBytes() > 2) {
      String sentence;
      int mnc2g, sinal, mnc4g, sinal4g;
      long userStatus;
      int mncoutro, count, deviceStatus, i;
      String license;
      int subtype = buf.readUnsignedByte();
      int length = buf.readUnsignedByte();
      int endIndex = buf.readerIndex() + length;
      switch (subtype) {
        case 1:
          position.set("odometer", Long.valueOf(buf.readUnsignedInt() * 100L));
          break;
        case 2:
          position.set("fuel", Double.valueOf(buf.readUnsignedShort() * 0.1D));
          break;
        case 48:
          position.set("rssi", Short.valueOf(buf.readUnsignedByte()));
          break;
        case 49:
          position.set("sat", Short.valueOf(buf.readUnsignedByte()));
          break;
        case 51:
          sentence = buf.readCharSequence(length, StandardCharsets.US_ASCII).toString();
          if (sentence.startsWith("*M00")) {
            String lockStatus = sentence.substring(8, 15);
            position.set("battery", Double.valueOf(Integer.parseInt(lockStatus.substring(2, 5)) * 0.01D));
          } 
          break;
        case 83:
          buf.readUnsignedByte();
          buf.readUnsignedShortLE();
          mnc2g = buf.readUnsignedByte();
          buf.readUnsignedShort();
          buf.readUnsignedShort();
          sinal = buf.readUnsignedByte();
          position.set("Conex", "2G");
          position.set("operator", decodeOperadora(mnc2g));
          position.set("Sinal", Integer.valueOf(sinal));
          break;
        case 93:
          buf.readUnsignedByte();
          buf.readUnsignedShortLE();
          mnc4g = buf.readUnsignedByte();
          buf.readUnsignedShort();
          buf.readUnsignedShort();
          sinal4g = buf.readUnsignedByte();
          position.set("Conex", "4G");
          position.set("operator", decodeOperadora(mnc4g));
          position.set("Sinal", Integer.valueOf(sinal4g));
          break;
        case 86:
          position.set("batteryLevel", Short.valueOf(buf.readUnsignedByte()));
          break;
        case 97:
          position.set("power", Double.valueOf(buf.readUnsignedShort() * 0.01D));
          break;
        case 241:
          if (length > 0)
            position.set("iccid", buf
                .readCharSequence(length, StandardCharsets.US_ASCII).toString()); 
          break;
        case 128:
          buf.readUnsignedByte();
          endIndex = buf.writerIndex() - 2;
          decodeExtension(position, buf, endIndex);
          break;
        case 145:
          position.set("battery", Double.valueOf(buf.readUnsignedShort() * 0.1D));
          position.set("rpm", Integer.valueOf(buf.readUnsignedShort()));
          position.set("obdSpeed", Short.valueOf(buf.readUnsignedByte()));
          position.set("throttle", Integer.valueOf(buf.readUnsignedByte() * 100 / 255));
          position.set("engineLoad", Integer.valueOf(buf.readUnsignedByte() * 100 / 255));
          position.set("coolantTemp", Integer.valueOf(buf.readUnsignedByte() - 40));
          buf.readUnsignedShort();
          position.set("fuelConsumption", Double.valueOf(buf.readUnsignedShort() * 0.01D));
          buf.readUnsignedShort();
          buf.readUnsignedInt();
          buf.readUnsignedShort();
          position.set("fuelUsed", Double.valueOf(buf.readUnsignedShort() * 0.01D));
          break;
        case 148:
          if (length > 0)
            position.set("vin", buf
                .readCharSequence(length, StandardCharsets.US_ASCII).toString()); 
          break;
        case 167:
          position.set("adc1", Integer.valueOf(buf.readUnsignedShort()));
          position.set("adc2", Integer.valueOf(buf.readUnsignedShort()));
          break;
        case 172:
          position.set("odometer", Long.valueOf(buf.readUnsignedInt()));
          break;
        case 208:
          userStatus = buf.readUnsignedInt();
          if (BitUtil.check(userStatus, 3))
            position.set("alarm", "vibration"); 
          break;
        case 210:
          buf.readUnsignedShortLE();
          mncoutro = buf.readUnsignedByte();
          position.set("operator", decodeOperadora(mncoutro));
          break;
        case 211:
          position.set("power", Double.valueOf(buf.readUnsignedShort() * 0.1D));
          break;
        case 212:
        case 254:
          position.set("batteryLevel", Short.valueOf(buf.readUnsignedByte()));
          break;
        case 214:
          position.set("iccid", Integer.valueOf(buf.readUnsignedShort()));
          break;
        case 213:
          if (length == 2) {
            position.set("battery", Double.valueOf(buf.readUnsignedShort() * 0.01D));
            break;
          } 
          count = buf.readUnsignedByte();
          for (i = 1; i <= count; i++) {
            position.set("lock" + i + "Id", ByteBufUtil.hexDump(buf.readSlice(5)));
            position.set("lock" + i + "Card", ByteBufUtil.hexDump(buf.readSlice(5)));
            position.set("lock" + i + "Battery", Short.valueOf(buf.readUnsignedByte()));
            int status = buf.readUnsignedShort();
            position.set("lock" + i + "Locked", Boolean.valueOf(!BitUtil.check(status, 5)));
          } 
          break;
        case 218:
          buf.readUnsignedShort();
          deviceStatus = buf.readUnsignedByte();
          position.set("string", Boolean.valueOf(BitUtil.check(deviceStatus, 0)));
          position.set("motion", Boolean.valueOf(BitUtil.check(deviceStatus, 2)));
          position.set("cover", Boolean.valueOf(BitUtil.check(deviceStatus, 3)));
          break;
        case 235:
          if (buf.getUnsignedShort(buf.readerIndex()) > 200) {
            Network network = new Network();
            int mcc = buf.readUnsignedShort();
            int mnc = buf.readUnsignedByte();
            while (buf.readerIndex() < endIndex)
              network.addCellTower(CellTower.from(mcc, mnc, buf
                    .readUnsignedShort(), buf.readUnsignedShort(), buf
                    .readUnsignedByte())); 
            position.setNetwork(network);
            break;
          } 
          while (buf.readerIndex() < endIndex) {
            int extendedLength = buf.readUnsignedShort();
            int extendedType = buf.readUnsignedShort();
            switch (extendedType) {
              case 1:
                position.set("fuel1", Double.valueOf(buf.readUnsignedShort() * 0.1D));
                buf.readUnsignedByte();
                continue;
              case 35:
                position.set("fuel2", Double.valueOf(Double.parseDouble(buf
                        .readCharSequence(6, StandardCharsets.US_ASCII).toString())));
                continue;
              case 206:
                position.set("power", Double.valueOf(buf.readUnsignedShort() * 0.01D));
                continue;
            } 
            buf.skipBytes(extendedLength - 2);
          } 
          break;
        case 237:
          license = buf.readCharSequence(length, StandardCharsets.US_ASCII).toString().trim();
          position.set("driverLicense", license);
          break;
        case 238:
          position.set("rssi", Short.valueOf(buf.readUnsignedByte()));
          position.set("power", Double.valueOf(buf.readUnsignedShort() * 0.001D));
          position.set("battery", Double.valueOf(buf.readUnsignedShort() * 0.001D));
          position.set("sat", Short.valueOf(buf.readUnsignedByte()));
          break;
      } 
      buf.readerIndex(endIndex);
    } 
    return position;
  }
  
  private Position decodeLocation2(DeviceSession deviceSession, ByteBuf buf, int type) {
    Position position = new Position(getProtocolName());
    position.setDeviceId(deviceSession.getDeviceId());
    decodeBinaryLocation(buf, position);
    position.setValid((type != 21762));
    position.set("rssi", Short.valueOf(buf.readUnsignedByte()));
    position.set("sat", Short.valueOf(buf.readUnsignedByte()));
    position.set("odometer", Long.valueOf(buf.readUnsignedInt() * 1000L));
    int battery = buf.readUnsignedByte();
    if (battery <= 100) {
      position.set("batteryLevel", Integer.valueOf(battery));
    } else if (battery == 170) {
      position.set("charge", Boolean.valueOf(true));
    } 
    position.setNetwork(new Network(CellTower.fromCidLac(
            getCacheManager().getConfig(), buf.readUnsignedInt(), buf.readUnsignedShort())));
    int product = buf.readUnsignedByte();
    int status = buf.readUnsignedShort();
    int alarm = buf.readUnsignedShort();
    if (product == 1 || product == 2) {
      if (BitUtil.check(alarm, 0))
        position.set("alarm", "lowPower"); 
    } else if (product == 3) {
      position.set("blocked", Boolean.valueOf(BitUtil.check(status, 5)));
      if (BitUtil.check(alarm, 1))
        position.set("alarm", "lowPower"); 
      if (BitUtil.check(alarm, 2))
        position.set("alarm", "vibration"); 
      if (BitUtil.check(alarm, 3))
        position.set("alarm", "lowBattery"); 
    } 
    position.set("status", Integer.valueOf(status));
    return position;
  }
  
  private List<Position> decodeLocationBatch(DeviceSession deviceSession, ByteBuf buf) {
    List<Position> positions = new LinkedList<>();
    int count = buf.readUnsignedShort();
    int locationType = buf.readUnsignedByte();
    for (int i = 0; i < count; i++) {
      int endIndex = buf.readUnsignedShort() + buf.readerIndex();
      Position position = decodeLocation(deviceSession, buf);
      if (locationType > 0)
        position.set("archive", Boolean.valueOf(true)); 
      positions.add(position);
      buf.readerIndex(endIndex);
    } 
    return positions;
  }
  
  private Position decodeTransparent(DeviceSession deviceSession, ByteBuf buf) {
    int type = buf.readUnsignedByte();
    if (type == 240) {
      int count, i;
      Position position = new Position(getProtocolName());
      position.setDeviceId(deviceSession.getDeviceId());
      Date time = readDate(buf, getTimeZone(deviceSession.getDeviceId(), "GMT+8"));
      if (buf.readUnsignedByte() > 0)
        position.set("archive", Boolean.valueOf(true)); 
      buf.readUnsignedByte();
      int subtype = buf.readUnsignedByte();
      switch (subtype) {
        case 1:
          count = buf.readUnsignedByte();
          for (i = 0; i < count; i++) {
            int id = buf.readUnsignedShort();
            int length = buf.readUnsignedByte();
            switch (id) {
              case 258:
              case 1320:
              case 1350:
                position.set("odometer", Long.valueOf(buf.readUnsignedInt() * 100L));
                break;
              case 259:
                position.set("fuel", Double.valueOf(buf.readUnsignedInt() * 0.01D));
                break;
              case 1322:
                position.set("fuel", Double.valueOf(buf.readUnsignedShort() * 0.01D));
                break;
              case 261:
              case 1324:
                position.set("fuelUsed", Double.valueOf(buf.readUnsignedInt() * 0.01D));
                break;
              case 330:
              case 1335:
              case 1336:
              case 1337:
                position.set("fuelConsumption", Double.valueOf(buf.readUnsignedShort() * 0.01D));
                break;
              default:
                switch (length) {
                  case 1:
                    position.set("io" + id, Short.valueOf(buf.readUnsignedByte()));
                    break;
                  case 2:
                    position.set("io" + id, Integer.valueOf(buf.readUnsignedShort()));
                    break;
                  case 4:
                    position.set("io" + id, Long.valueOf(buf.readUnsignedInt()));
                    break;
                } 
                buf.skipBytes(length);
                break;
            } 
          } 
          decodeCoordinates(position, buf);
          position.setTime(time);
          return position;
        case 3:
          count = buf.readUnsignedByte();
          for (i = 0; i < count; i++) {
            int id = buf.readUnsignedShort();
            int length = buf.readUnsignedByte();
            switch (id) {
              case 26:
                position.set("alarm", "hardAcceleration");
                break;
              case 27:
                position.set("alarm", "hardBraking");
                break;
              case 28:
                position.set("alarm", "hardCornering");
                break;
              case 29:
              case 30:
              case 31:
                position.set("alarm", "laneChange");
                break;
              case 35:
                position.set("alarm", "fatigueDriving");
                break;
            } 
            buf.skipBytes(length);
          } 
          decodeCoordinates(position, buf);
          position.setTime(time);
          return position;
        case 11:
          if (buf.readUnsignedByte() > 0)
            position.set("vin", buf.readCharSequence(17, StandardCharsets.US_ASCII).toString()); 
          getLastLocation(position, time);
          return position;
      } 
      return null;
    } 
    return null;
  }
}
