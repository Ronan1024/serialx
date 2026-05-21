package com.ronan.serialx.utils;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;

/**
 * 本机 IP 工具。
 */
public final class IpUtil {

    private static final String LOCALHOST = "127.0.0.1";

    private static final List<String> EXCLUDED_NAME_PREFIXES = List.of(
            "lo", "docker", "br-", "veth", "vmnet", "vboxnet", "utun", "tun", "tap", "awdl", "llw", "bridge");

    private static final List<String> PREFERRED_NAME_PREFIXES = List.of("en0", "eth0", "wlan0", "en", "eth", "wlan");

    private IpUtil() {
    }

    public static String getLocalIp() {
        try {
            List<IpCandidate> candidates = new ArrayList<>();
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                if (!isValidInterface(networkInterface)) {
                    continue;
                }
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (isValidAddress(address)) {
                        candidates.add(new IpCandidate(networkInterface.getName(), address));
                    }
                }
            }
            return candidates.stream()
                    .min(Comparator.comparingInt(IpUtil::score))
                    .map(candidate -> candidate.address().getHostAddress())
                    .orElse(LOCALHOST);
        } catch (Exception e) {
            throw new IllegalStateException("获取本机 IP 失败", e);
        }
    }

    private static boolean isValidInterface(NetworkInterface networkInterface) throws SocketException {
        if (!networkInterface.isUp()
                || networkInterface.isLoopback()
                || networkInterface.isVirtual()
                || networkInterface.isPointToPoint()) {
            return false;
        }
        String name = networkInterface.getName();
        return EXCLUDED_NAME_PREFIXES.stream().noneMatch(name::startsWith);
    }

    private static boolean isValidAddress(InetAddress address) {
        return address instanceof Inet4Address
                && !address.isAnyLocalAddress()
                && !address.isLoopbackAddress()
                && !address.isLinkLocalAddress();
    }

    private static int score(IpCandidate candidate) {
        String interfaceName = candidate.interfaceName();
        for (int index = 0; index < PREFERRED_NAME_PREFIXES.size(); index++) {
            if (interfaceName.startsWith(PREFERRED_NAME_PREFIXES.get(index))) {
                return index;
            }
        }
        return candidate.address().isSiteLocalAddress() ? PREFERRED_NAME_PREFIXES.size() : PREFERRED_NAME_PREFIXES.size() + 1;
    }

    private record IpCandidate(String interfaceName, InetAddress address) {
    }
}
