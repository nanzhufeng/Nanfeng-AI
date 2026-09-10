import Cocoa

guard CommandLine.arguments.count == 2, let targetPid = Int(CommandLine.arguments[1]) else {
    exit(2)
}

let windows = CGWindowListCopyWindowInfo([.optionOnScreenOnly], kCGNullWindowID) as? [[String: Any]] ?? []
for window in windows {
    guard (window[kCGWindowOwnerPID as String] as? Int) == targetPid,
          (window[kCGWindowLayer as String] as? Int) == 0,
          let number = window[kCGWindowNumber as String] as? Int,
          let bounds = window[kCGWindowBounds as String] as? [String: Any]
    else { continue }
    let name = window[kCGWindowName as String] as? String ?? ""
    let width = bounds["Width"] as? Int ?? 0
    let height = bounds["Height"] as? Int ?? 0
    print("\(number)\t\(width)\t\(height)\t\(name)")
    exit(0)
}
exit(1)
