#import <React/RCTBridgeModule.h>

@interface RCT_EXTERN_MODULE(FlowGuardCertPinning, NSObject)

RCT_EXTERN_METHOD(secureFetch:(NSString *)url
                  options:(NSDictionary *)options
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)

@end
