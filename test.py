import requests
import json

url = "https://api.kie.ai/api/v1/jobs/createTask"

payload = json.dumps({
   "model": "nano-banana-2",
   "callBackUrl": "https://your-domain.com/api/callback",
   "input": {
      "prompt": "Read my image, and then extract the illustrations within it." or "Read my photo and cut out its illustrations.",
                "image_input": ["https://s.500fd.com/tt_product/fa1b3479734448a99b54d66044672762~tplv-dx0w9n1ysr-resize-jpeg:800:800.jpeg"],
      "aspect_ratio": "auto",
      "resolution": "1K",
      "output_format": "png"
   }
})
headers = {
   'Authorization': 'Bearer 10ac48445429ff287778affa0caa0612',
   'Content-Type': 'application/json'
}

response = requests.request("POST", url, headers=headers, data=payload)

# print(response.text)
{"code":200,"msg":"success","data":{"taskId":"ab98ca21ae731b9888fd7c9662b2bf0a","recordId":"ab98ca21ae731b9888fd7c9662b2bf0a"}}
import requests

url = "https://api.kie.ai/api/v1/jobs/recordInfo?"

payload = {'taskId':"ab98ca21ae731b9888fd7c9662b2bf0a"}


response = requests.request("GET", url, headers=headers, params=payload)

print(response.text)