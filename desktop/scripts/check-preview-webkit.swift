import Cocoa
import WebKit

final class Runner: NSObject, WKNavigationDelegate {
    var index = 0
    let kinds = ["image", "pdf", "video", "audio", "text", "boundary", "menu"]
    var results: [[String: Any]] = []
    var window: NSWindow!
    var web: WKWebView!
    func start() {
        let config = WKWebViewConfiguration()
        config.websiteDataStore = .nonPersistent()
        web = WKWebView(frame: NSRect(x: 0,y: 0,width: 1280,height: 800), configuration: config)
        web.navigationDelegate = self
        window = NSWindow(contentRect: web.frame, styleMask: [.titled], backing: .buffered, defer: false)
        window.title = "南枫预览隔离验证"
        window.contentView = web
        window.orderBack(nil)
        load()
        DispatchQueue.main.asyncAfter(deadline: .now()+40) { fputs("WKWebView validation timed out\n",stderr); exit(2) }
    }
    func load() {
        if index == kinds.count {
            let data = try! JSONSerialization.data(withJSONObject: results, options: [.prettyPrinted,.sortedKeys])
            print(String(data:data,encoding:.utf8)!)
            window.close()
            exit(0)
        }
        let path = "\(CommandLine.arguments[1])/\(kinds[index]).html"
        let html = try! String(contentsOfFile:path,encoding:.utf8)
        web.loadHTMLString(html, baseURL:nil)
    }
    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        DispatchQueue.main.asyncAfter(deadline: .now()+0.2) {
            let js = """
            (()=>{if(document.querySelector('details')) { const p=document.querySelector('[popover]'), r=p.getBoundingClientRect(); return JSON.stringify({menuVisible:p.matches(':popover-open')&&r.top>=8&&r.bottom<=innerHeight-8&&p.contains(document.elementFromPoint(r.x+10,r.y+10))}); } const d=document.querySelector('.image-preview-dialog'),r=d.getBoundingClientRect(),b=document.querySelector('.image-preview-viewport,.pdf-preview-frame,.video-preview-frame,.local-text-preview')?.getBoundingClientRect();return JSON.stringify({width:r.width,height:r.height,viewWidth:innerWidth,viewHeight:innerHeight,x:r.x,y:r.y,overflow:d.scrollHeight>d.clientHeight+1,overlayOnTop:!!document.elementFromPoint(262,innerHeight/2)?.closest('.attachment-preview-overlay'),imageReady:[...document.querySelectorAll(".image-preview-dialog img")].every(i=>i.complete&&i.naturalWidth>0),mediaReady:!document.querySelector("video,audio")||document.querySelector("video,audio").readyState>0,mediaDuration:document.querySelector("video,audio")?.duration||0,bodyFits:!b||(b.height>0&&b.top>=0&&b.bottom<=innerHeight)})})()
            """
            self.web.evaluateJavaScript(js) { value,error in
                guard error == nil, let raw=value as? String, let data=raw.data(using:.utf8), var r=(try? JSONSerialization.jsonObject(with:data)) as? [String:Any] else { fputs("WK readback failed\n",stderr);exit(3) }
                let ok=(r["menuVisible"] as? Bool == true) || (r["width"] as? Double)==(r["viewWidth"] as? Double) && (r["height"] as? Double)==(r["viewHeight"] as? Double) && (r["x"] as? Double)==0 && (r["y"] as? Double)==0 && r["overflow"] as? Bool == false && r["overlayOnTop"] as? Bool == true && r["bodyFits"] as? Bool == true && r["mediaReady"] as? Bool == true && r["imageReady"] as? Bool == true
                r["kind"]=self.kinds[self.index];r["passed"]=ok;self.results.append(r)
                if !ok { print(r);exit(4) }
                if self.kinds[self.index] == "pdf" {
                    self.web.takeSnapshot(with:nil) { image,_ in
                        if let data=image?.tiffRepresentation,let bitmap=NSBitmapImageRep(data:data),let png=bitmap.representation(using:.png,properties:[:]) { try? png.write(to:URL(fileURLWithPath:CommandLine.arguments[2])) }
                        self.index += 1;self.load()
                    }
                } else {self.index += 1;self.load()}
            }
        }
    }
}
let app=NSApplication.shared
app.setActivationPolicy(.accessory)
guard CommandLine.arguments.count == 3 else { fputs("Usage: runner FIXTURE_DIR PDF_SCREENSHOT_PATH\n",stderr);exit(1) }
let runner=Runner();runner.start();app.run()
