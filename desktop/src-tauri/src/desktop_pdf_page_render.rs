//! PDF content is rasterized locally, never loaded as an iframe/plugin or executable document.
#[cfg(target_os = "macos")]
pub fn render_page(bytes: &[u8], page_number: u32) -> Result<Vec<u8>, String> {
    use image::{DynamicImage, ImageFormat, RgbaImage};
    use std::{ffi::c_void, io::Cursor, ptr};
    #[repr(C)]
    #[derive(Clone, Copy)]
    struct Point {
        x: f64,
        y: f64,
    }
    #[repr(C)]
    #[derive(Clone, Copy)]
    struct Size {
        width: f64,
        height: f64,
    }
    #[repr(C)]
    #[derive(Clone, Copy)]
    struct Rect {
        origin: Point,
        size: Size,
    }
    #[repr(C)]
    struct Transform {
        a: f64,
        b: f64,
        c: f64,
        d: f64,
        tx: f64,
        ty: f64,
    }
    type Handle = *mut c_void;
    #[link(name = "CoreGraphics", kind = "framework")]
    unsafe extern "C" {
        fn CGDataProviderCreateWithData(
            info: Handle,
            data: *const c_void,
            size: usize,
            release: Option<unsafe extern "C" fn(Handle, *const c_void, usize)>,
        ) -> Handle;
        fn CGDataProviderRelease(value: Handle);
        fn CGPDFDocumentCreateWithProvider(provider: Handle) -> Handle;
        fn CGPDFDocumentRelease(value: Handle);
        fn CGPDFDocumentGetPage(document: Handle, page: usize) -> Handle;
        fn CGPDFPageGetBoxRect(page: Handle, box_type: i32) -> Rect;
        fn CGPDFPageGetRotationAngle(page: Handle) -> i32;
        fn CGPDFPageGetDrawingTransform(
            page: Handle,
            box_type: i32,
            rect: Rect,
            rotate: i32,
            preserve: bool,
        ) -> Transform;
        fn CGColorSpaceCreateDeviceRGB() -> Handle;
        fn CGColorSpaceRelease(value: Handle);
        fn CGBitmapContextCreate(
            data: Handle,
            width: usize,
            height: usize,
            bits: usize,
            row: usize,
            color: Handle,
            info: u32,
        ) -> Handle;
        fn CGContextRelease(value: Handle);
        fn CGContextSetRGBFillColor(context: Handle, r: f64, g: f64, b: f64, a: f64);
        fn CGContextFillRect(context: Handle, rect: Rect);
        fn CGContextConcatCTM(context: Handle, transform: Transform);
        fn CGContextScaleCTM(context: Handle, x: f64, y: f64);
        fn CGContextDrawPDFPage(context: Handle, page: Handle);
    }
    struct Owned(Handle, unsafe extern "C" fn(Handle));
    impl Drop for Owned {
        fn drop(&mut self) {
            unsafe { (self.1)(self.0) }
        }
    }
    fn owned(handle: Handle, release: unsafe extern "C" fn(Handle)) -> Result<Owned, String> {
        if handle.is_null() {
            Err("本地 PDF 页面无法绘制".into())
        } else {
            Ok(Owned(handle, release))
        }
    }
    // All borrowed bytes and bitmap storage outlive the handles; no paths or callbacks escape.
    unsafe {
        let provider = owned(
            CGDataProviderCreateWithData(ptr::null_mut(), bytes.as_ptr().cast(), bytes.len(), None),
            CGDataProviderRelease,
        )?;
        let document = owned(
            CGPDFDocumentCreateWithProvider(provider.0),
            CGPDFDocumentRelease,
        )?;
        let page = CGPDFDocumentGetPage(document.0, page_number as usize);
        if page.is_null() {
            return Err("本地 PDF 页码不可用".into());
        }
        let bounds = CGPDFPageGetBoxRect(page, 1); // CropBox, falling back to MediaBox in CoreGraphics.
        let (mut width, mut height) = (bounds.size.width, bounds.size.height);
        if !width.is_finite() || !height.is_finite() || width <= 0.0 || height <= 0.0 {
            return Err("本地 PDF 页面尺寸无效".into());
        }
        if CGPDFPageGetRotationAngle(page).rem_euclid(180) != 0 {
            std::mem::swap(&mut width, &mut height);
        }
        let (page_width, page_height) = (width, height);
        let scale = 2048.0 / width.max(height);
        let (width, height) = (
            (width * scale).round().max(1.0) as u32,
            (height * scale).round().max(1.0) as u32,
        );
        let mut pixels = vec![255u8; (width as usize) * (height as usize) * 4];
        let color = owned(CGColorSpaceCreateDeviceRGB(), CGColorSpaceRelease)?;
        let context = owned(
            CGBitmapContextCreate(
                pixels.as_mut_ptr().cast(),
                width as usize,
                height as usize,
                8,
                width as usize * 4,
                color.0,
                1 | (4 << 12),
            ),
            CGContextRelease,
        )?;
        let rect = Rect {
            origin: Point { x: 0.0, y: 0.0 },
            size: Size {
                width: width as f64,
                height: height as f64,
            },
        };
        CGContextSetRGBFillColor(context.0, 1.0, 1.0, 1.0, 1.0);
        CGContextFillRect(context.0, rect);
        CGContextScaleCTM(
            context.0,
            width as f64 / page_width,
            height as f64 / page_height,
        );
        let rect = Rect {
            origin: Point { x: 0.0, y: 0.0 },
            size: Size {
                width: page_width,
                height: page_height,
            },
        };
        CGContextConcatCTM(
            context.0,
            CGPDFPageGetDrawingTransform(page, 1, rect, 0, true),
        );
        CGContextDrawPDFPage(context.0, page);
        drop(context);
        let image = RgbaImage::from_raw(width, height, pixels).ok_or("本地 PDF 页面编码失败")?;
        let mut output = Cursor::new(Vec::new());
        DynamicImage::ImageRgba8(image)
            .write_to(&mut output, ImageFormat::Png)
            .map_err(|_| "本地 PDF 页面编码失败")?;
        Ok(output.into_inner())
    }
}
#[cfg(not(target_os = "macos"))]
pub fn render_page(_bytes: &[u8], _page_number: u32) -> Result<Vec<u8>, String> {
    Err("当前平台尚未提供本地 PDF 页面绘制".into())
}

#[cfg(all(test, target_os = "macos"))]
mod tests {
    use super::render_page;
    const PDF: &[u8] = include_bytes!("../tests/fixtures/preview-page.pdf");
    #[test]
    fn pdf_page_renders_real_ink_with_bounded_dimensions() {
        let png = render_page(PDF, 1).unwrap();
        let image = image::load_from_memory(&png).unwrap().to_rgba8();
        assert_eq!((image.width(), image.height()), (1229, 2048));
        let dark = image
            .pixels()
            .filter(|p| p.0[0] < 128 && p.0[1] < 128 && p.0[2] < 128)
            .count();
        assert!(dark > 500, "a successful but blank page must fail");
        let top_ink = image
            .enumerate_pixels()
            .filter(|(_, y, p)| *y < image.height() / 4 && p.0[0] < 128)
            .count();
        let bottom_ink = image
            .enumerate_pixels()
            .filter(|(_, y, p)| *y > image.height() * 3 / 4 && p.0[0] < 128)
            .count();
        assert!(
            top_ink > 500 && bottom_ink > 500,
            "page must fill the raster, preserving top and bottom content"
        );
        if let Ok(path) = std::env::var("NANFENG_PDF_TEST_PNG") {
            std::fs::write(path, png).unwrap();
        }
    }
    #[test]
    fn corrupt_documents_and_missing_pages_fail_explicitly() {
        assert!(render_page(b"not a PDF", 1).is_err());
        assert!(render_page(PDF, 0).is_err());
        assert!(render_page(PDF, 2).is_err());
    }
}
